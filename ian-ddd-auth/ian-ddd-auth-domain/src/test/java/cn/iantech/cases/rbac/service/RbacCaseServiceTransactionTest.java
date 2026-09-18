package cn.iantech.cases.rbac.service;

import cn.iantech.cases.model.Actor;
import cn.iantech.cases.rbac.model.RbacCaseCommands.CreateAccount;
import cn.iantech.cases.rbac.model.RbacCaseCommands.CreateUser;
import cn.iantech.cases.rbac.model.RbacCaseCommands.UpdateUser;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.rbac.model.RbacPermissionCode;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import cn.iantech.domain.rbac.service.impl.RbacAccessControlService;
import cn.iantech.domain.rbac.service.impl.RbacAccountService;
import cn.iantech.domain.rbac.service.impl.RbacDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 口令入口的事务边界：<b>编码必须在事务外、落库必须在事务内、失败整体回滚</b>。
 *
 * <p>通过真实 Spring Bean 调用（而非 new 裸对象）验证，并覆盖权限校验与编码的先后顺序、
 * 空白口令不改密、以及外层事务存在时拒绝编码。</p>
 */
class RbacCaseServiceTransactionTest {

    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(TestConfig.class);
    private final IPasswordEncoder passwordEncoder = context.getBean(IPasswordEncoder.class);
    private final RbacDomainService domainService = context.getBean(RbacDomainService.class);
    private final RbacAccountService accountService = context.getBean(RbacAccountService.class);
    private final RbacAccessControlService accessControlService =
            context.getBean(RbacAccessControlService.class);

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void shouldEncodeOutsideTransactionAndPersistInsideTransaction() {
        RbacCaseService service = context.getBean(RbacCaseService.class);
        AtomicBoolean activeDuringEncode = new AtomicBoolean(true);
        AtomicBoolean activeDuringPersist = new AtomicBoolean(false);
        when(passwordEncoder.encode("Pwd@0001")).thenAnswer(invocation -> {
            activeDuringEncode.set(TransactionSynchronizationManager.isActualTransactionActive());
            return "encoded-password";
        });
        when(accountService.createAccount(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    activeDuringPersist.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return RbacAccountEntity.builder().id(1L).username("admin").build();
                });

        service.createAccount(new CreateAccount("admin", "Pwd@0001", "管理员", "admin@test.com", "13800000000"));

        assertFalse(activeDuringEncode.get(), "口令编码必须在事务外执行");
        assertTrue(activeDuringPersist.get(), "数据库写入必须在事务内执行");
        verify(accountService).createAccount(eq("admin"), eq("encoded-password"), eq("管理员"),
                eq("admin@test.com"), eq("13800000000"));
    }

    @Test
    void shouldCommitOnceOnSuccess() {
        RbacCaseService service = context.getBean(RbacCaseService.class);
        TrackingTransactionManager manager = context.getBean(TrackingTransactionManager.class);
        stubCreateUserSuccess();

        service.createUser(actor(), new CreateUser("bob", "Pwd@0001", "Bob", null, null, true));

        assertEquals(1, manager.commits.get());
        assertEquals(0, manager.rollbacks.get());
    }

    @Test
    void shouldRollbackWhenWritingFails() {
        RbacCaseService service = context.getBean(RbacCaseService.class);
        TrackingTransactionManager manager = context.getBean(TrackingTransactionManager.class);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(domainService.createUser(anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("权限初始化失败"));

        assertThrows(IllegalStateException.class,
                () -> service.createUser(actor(), new CreateUser("bob", "Pwd@0001", "Bob", null, null, true)));

        assertEquals(0, manager.commits.get(), "写入失败不得提交");
        assertEquals(1, manager.rollbacks.get(), "写入失败必须整体回滚");
    }

    @Test
    void shouldNotEncodeWhenPasswordIsBlankOnUpdate() {
        RbacCaseService service = context.getBean(RbacCaseService.class);

        service.updateUser(actor(), new UpdateUser(1L, "   ", "新名称", null, null, null));

        verify(passwordEncoder, never()).encode(anyString());
        ArgumentCaptor<String> passwordHashCaptor = ArgumentCaptor.forClass(String.class);
        verify(domainService).updateUser(eq(1L), eq(1L), passwordHashCaptor.capture(), eq("新名称"),
                isNull(), isNull(), isNull());
        assertNull(passwordHashCaptor.getValue(), "空白口令必须以 null 传入写入阶段，表示不改密");
    }

    @Test
    void shouldRejectIllegalPasswordBeforeEncoding() {
        RbacCaseService service = context.getBean(RbacCaseService.class);

        assertThrows(AppException.class,
                () -> service.createUser(actor(), new CreateUser("bob", "1234567", "Bob", null, null, true)));

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void shouldCheckPermissionBeforeEncoding() {
        RbacCaseService service = context.getBean(RbacCaseService.class);
        doThrow(new AppException("ACCESS_DENIED", "无权访问")).when(accessControlService)
                .authorize(anyLong(), anyLong(), anyString(), any(RbacPermissionCode.class));

        assertThrows(AppException.class,
                () -> service.createUser(actor(), new CreateUser("bob", "Pwd@0001", "Bob", null, null, true)));

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void shouldRejectEncodingWhenOuterTransactionIsActive() {
        RbacCaseService service = context.getBean(RbacCaseService.class);
        TransactionTemplate outerTransaction = context.getBean(TransactionTemplate.class);
        stubCreateUserSuccess();

        assertThrows(IllegalStateException.class, () -> outerTransaction.execute(status ->
                service.createUser(actor(), new CreateUser("bob", "Pwd@0001", "Bob", null, null, true))));

        verify(passwordEncoder, never()).encode(anyString());
    }

    private void stubCreateUserSuccess() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(domainService.createUser(anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(RbacUserEntity.builder().id(2L).username("bob").build());
    }

    private Actor actor() {
        return new Actor("request-1", 1L, 1L, "root", "ADMIN_PRIMARY", "gateway");
    }

    @Configuration
    static class TestConfig {

        @Bean
        TrackingTransactionManager transactionManager() {
            return new TrackingTransactionManager();
        }

        @Bean
        TransactionTemplate transactionTemplate(TrackingTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        IPasswordEncoder passwordEncoder() {
            return mock(IPasswordEncoder.class);
        }

        @Bean
        RbacDomainService rbacDomainService() {
            return mock(RbacDomainService.class);
        }

        @Bean
        RbacAccountService rbacAccountService() {
            return mock(RbacAccountService.class);
        }

        @Bean
        RbacAccessControlService rbacAccessControlService() {
            return mock(RbacAccessControlService.class);
        }

        @Bean
        RbacCaseService rbacCaseService(RbacDomainService rbacDomainService,
                                        RbacAccountService rbacAccountService,
                                        RbacAccessControlService rbacAccessControlService,
                                        IPasswordEncoder passwordEncoder,
                                        TransactionTemplate transactionTemplate) {
            return new RbacCaseService(rbacDomainService, rbacAccountService, rbacAccessControlService,
                    passwordEncoder, transactionTemplate);
        }
    }

    /**
     * 只记录事务生命周期、不持有外部资源的事务管理器，用于断言提交与回滚次数。
     */
    static final class TrackingTransactionManager extends AbstractPlatformTransactionManager {
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger rollbacks = new AtomicInteger();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // 测试事务管理器不需要开启真实资源
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits.incrementAndGet();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks.incrementAndGet();
        }
    }
}
