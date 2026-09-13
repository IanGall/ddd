package cn.iantech.infrastructure.persistent.repository;

import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.infrastructure.persistent.dao.*;
import cn.iantech.infrastructure.persistent.po.*;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

class PersistenceGlobalIdRepositoryTest {

    @Test
    void shouldGenerateIdBeforeSavingRbacAccount() {
        IRbacAccountDao dao = mock(IRbacAccountDao.class);
        Converter converter = mock(Converter.class);
        GlobalIdGenerator generator = mock(GlobalIdGenerator.class);
        RbacAccountEntity entity = RbacAccountEntity.builder().username("admin").build();
        RbacAccountPO po = new RbacAccountPO();
        RbacAccountEntity saved = RbacAccountEntity.builder().id(101L).username("admin").build();
        when(converter.convert(same(entity), eq(RbacAccountPO.class))).thenReturn(po);
        when(converter.convert(same(po), eq(RbacAccountEntity.class))).thenReturn(saved);
        when(generator.nextId()).thenReturn(101L);

        RbacAccountEntity result = new RbacAccountRepository(dao, converter, generator).save(entity);

        assertEquals(101L, po.getId());
        assertEquals(101L, result.getId());
        verify(dao).insert(same(po));
    }

    @Test
    void shouldReplaceProvidedRbacAccountIdWithGlobalId() {
        IRbacAccountDao dao = mock(IRbacAccountDao.class);
        Converter converter = mock(Converter.class);
        GlobalIdGenerator generator = mock(GlobalIdGenerator.class);
        RbacAccountEntity entity = RbacAccountEntity.builder().id(99L).build();
        RbacAccountPO po = new RbacAccountPO();
        po.setId(99L);
        RbacAccountEntity saved = RbacAccountEntity.builder().id(105L).build();
        when(converter.convert(same(entity), eq(RbacAccountPO.class))).thenReturn(po);
        when(converter.convert(same(po), eq(RbacAccountEntity.class))).thenReturn(saved);
        when(generator.nextId()).thenReturn(105L);

        new RbacAccountRepository(dao, converter, generator).save(entity);

        assertEquals(105L, po.getId());
        verify(generator).nextId();
    }

    @Test
    void shouldGenerateIdBeforeSavingRbacUser() {
        IRbacUserDao dao = mock(IRbacUserDao.class);
        Converter converter = mock(Converter.class);
        GlobalIdGenerator generator = mock(GlobalIdGenerator.class);
        RbacUserEntity entity = RbacUserEntity.builder().username("operator").build();
        RbacUserPO po = new RbacUserPO();
        RbacUserEntity saved = RbacUserEntity.builder().id(102L).accountId(10L).build();
        when(converter.convert(same(entity), eq(RbacUserPO.class))).thenReturn(po);
        when(converter.convert(same(po), eq(RbacUserEntity.class))).thenReturn(saved);
        when(generator.nextId()).thenReturn(102L);

        new RbacUserRepository(dao, converter, generator).save(10L, entity);

        assertEquals(102L, po.getId());
        assertEquals(10L, po.getAccountId());
        verify(dao).insert(same(po));
    }

    @Test
    void shouldGenerateIdBeforeSavingCustomerUser() {
        ICustomerUserDao dao = mock(ICustomerUserDao.class);
        Converter converter = mock(Converter.class);
        GlobalIdGenerator generator = mock(GlobalIdGenerator.class);
        CustomerUserEntity entity = CustomerUserEntity.builder().loginName("customer").build();
        CustomerUserPO po = new CustomerUserPO();
        CustomerUserEntity saved = CustomerUserEntity.builder().id(103L).loginName("customer").build();
        when(converter.convert(same(entity), eq(CustomerUserPO.class))).thenReturn(po);
        when(converter.convert(same(po), eq(CustomerUserEntity.class))).thenReturn(saved);
        when(generator.nextId()).thenReturn(103L);

        new CustomerUserRepository(dao, converter, generator).save(entity);

        assertEquals(103L, po.getId());
        verify(dao).insert(same(po));
    }

    @Test
    void shouldGenerateIdBeforeSavingChannelCredential() {
        IChannelCredentialDao dao = mock(IChannelCredentialDao.class);
        Converter converter = mock(Converter.class);
        GlobalIdGenerator generator = mock(GlobalIdGenerator.class);
        ChannelCredentialEntity entity = ChannelCredentialEntity.builder().channelCode("channel").build();
        ChannelCredentialPO po = new ChannelCredentialPO();
        when(converter.convert(same(entity), eq(ChannelCredentialPO.class))).thenReturn(po);
        when(generator.nextId()).thenReturn(104L);

        ChannelCredentialEntity result = new ChannelCredentialRepository(dao, converter, generator).save(entity);

        assertEquals(104L, po.getId());
        assertEquals(104L, result.getId());
        verify(dao).insert(same(po));
    }

    @Test
    void shouldGenerateUniqueIdsForChannelDataScopeBatch() {
        IChannelDataScopeDao dao = mock(IChannelDataScopeDao.class);
        GlobalIdGenerator generator = mock(GlobalIdGenerator.class);
        when(generator.nextId()).thenReturn(201L, 202L, 203L);

        new ChannelDataScopeRepository(dao, generator)
                .replace(20L, "STORE", List.of("A", "B", "C"), 30L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChannelDataScopePO>> captor = ArgumentCaptor.forClass(List.class);
        verify(dao).insertBatch(captor.capture());
        List<ChannelDataScopePO> items = captor.getValue();
        assertIterableEquals(List.of(201L, 202L, 203L), items.stream().map(ChannelDataScopePO::getId).toList());
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
}
