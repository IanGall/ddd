package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import cn.iantech.infrastructure.id.AuthIdBusiness;
import cn.iantech.infrastructure.persistent.dao.*;
import cn.iantech.infrastructure.persistent.po.*;
import cn.iantech.mysql.annotation.IdGenerator;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/**
 * 主键赋值已从 Repository 内联搬到持久化对象的 {@code @IdGenerator} 注解 + insert 拦截器，
 * 本测试锁两件事：Repository 不再赋 id，以及哪些表声明了注解、哪些没有。
 *
 * <p>「注解 → 拦截器 → 落库」这条链路由 ddd-mysql-starter 的
 * {@code IdAutoFillMybatisIntegrationTest}（真实 MyBatis + H2）与
 * {@code RbacServiceMysqlTest}（真实 MySQL）覆盖，这里不做重复验证。
 */
class PersistenceIdAssignmentRepositoryTest {

    @Test
    void shouldNotAssignIdInRbacAccountRepository() {
        IRbacAccountDao dao = mock(IRbacAccountDao.class);
        Converter converter = mock(Converter.class);
        RbacAccountEntity entity = RbacAccountEntity.builder().username("admin").build();
        RbacAccountPO po = new RbacAccountPO();
        when(converter.convert(same(entity), eq(RbacAccountPO.class))).thenReturn(po);

        new RbacAccountRepository(dao, converter).save(entity);

        assertNull(po.getId(), "主键由 insert 时的拦截器填充，Repository 不再赋 id");
        verify(dao).insert(same(po));
    }

    @Test
    void shouldNotAssignIdInRbacUserRepository() {
        IRbacUserDao dao = mock(IRbacUserDao.class);
        Converter converter = mock(Converter.class);
        RbacUserEntity entity = RbacUserEntity.builder().username("operator").build();
        RbacUserPO po = new RbacUserPO();
        when(converter.convert(same(entity), eq(RbacUserPO.class))).thenReturn(po);

        new RbacUserRepository(dao, converter).save(10L, entity);

        assertNull(po.getId(), "主键由 insert 时的拦截器填充，Repository 不再赋 id");
        assertEquals(10L, po.getAccountId());
        verify(dao).insert(same(po));
    }

    @Test
    void shouldNotAssignIdInCustomerUserRepository() {
        ICustomerUserDao dao = mock(ICustomerUserDao.class);
        Converter converter = mock(Converter.class);
        CustomerUserEntity entity = CustomerUserEntity.builder().loginName("customer").build();
        CustomerUserPO po = new CustomerUserPO();
        when(converter.convert(same(entity), eq(CustomerUserPO.class))).thenReturn(po);

        new CustomerUserRepository(dao, converter).save(entity);

        assertNull(po.getId(), "主键由 insert 时的拦截器填充，Repository 不再赋 id");
        verify(dao).insert(same(po));
    }

    @Test
    void shouldNotAssignIdInChannelCredentialRepositoryAndReadBackWhatInterceptorWrote() {
        IChannelCredentialDao dao = mock(IChannelCredentialDao.class);
        Converter converter = mock(Converter.class);
        ChannelCredentialEntity entity = ChannelCredentialEntity.builder().channelCode("channel").build();
        ChannelCredentialPO po = new ChannelCredentialPO();
        when(converter.convert(same(entity), eq(ChannelCredentialPO.class))).thenReturn(po);
        // 拦截器在 insert 期间把 id 写进 PO，Repository 负责回读给领域对象
        doAnswer(invocation -> {
            po.setId(104L);
            return 1;
        }).when(dao).insert(same(po));

        ChannelCredentialEntity result = new ChannelCredentialRepository(dao, converter).save(entity);

        assertEquals(104L, result.getId());
    }

    @Test
    void shouldDeclareIdentityBusinessOnSharedIdentityEntities() {
        // 这三张表的 id 都会流进 AuthSession.userId，必须共用一个序列
        assertEquals(AuthIdBusiness.IDENTITY, businessOf(RbacAccountPO.class));
        assertEquals(AuthIdBusiness.IDENTITY, businessOf(RbacUserPO.class));
        assertEquals(AuthIdBusiness.IDENTITY, businessOf(CustomerUserPO.class));
        assertEquals(AuthIdBusiness.CHANNEL_CREDENTIAL, businessOf(ChannelCredentialPO.class));
    }

    @Test
    void shouldNotDeclareIdGeneratorOnAutoIncrementEntities() {
        assertNull(businessOf(RbacRolePO.class), "rbac_role 用数据库自增");
        assertNull(businessOf(RbacPermissionPO.class), "rbac_permission 用数据库自增");
        assertNull(businessOf(ChannelDataScopePO.class), "channel_data_scope 用数据库自增");
        assertNull(businessOf(UserOrderPO.class), "分片表 user_order 必须保留自增主键");
    }

    @Test
    void shouldLeaveChannelDataScopeIdToDatabase() {
        IChannelDataScopeDao dao = mock(IChannelDataScopeDao.class);

        new ChannelDataScopeRepository(dao).replace(20L, "STORE", List.of("A", "B", "C"), 30L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChannelDataScopePO>> captor = ArgumentCaptor.forClass(List.class);
        verify(dao).insertBatch(captor.capture());
        List<ChannelDataScopePO> items = captor.getValue();
        // 主键由数据库自增，应用不赋 ID
        assertTrue(items.stream().allMatch(item -> item.getId() == null));
        assertIterableEquals(List.of("A", "B", "C"), items.stream().map(ChannelDataScopePO::getScopeValue).toList());
        items.forEach(item -> {
            assertEquals(20L, item.getChannelId());
            assertEquals("STORE", item.getScopeType());
            assertEquals(30L, item.getCreatedByUserId());
            assertEquals(30L, item.getUpdatedByUserId());
            assertTrue(item.getStatus());
            assertFalse(item.getDeleted());
        });
    }

    private static String businessOf(Class<?> type) {
        IdGenerator annotation = type.getAnnotation(IdGenerator.class);
        return annotation == null ? null : annotation.value();
    }
}
