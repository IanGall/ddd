package cn.iantech.cases.channel.service;

import cn.iantech.cases.channel.model.ChannelCaseModels.ReplaceDataScopes;
import cn.iantech.cases.model.Actor;
import cn.iantech.domain.channel.infra.IChannelDataScopeRepository;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.channel.service.IChannelCredentialDomainService;
import cn.iantech.domain.rbac.service.IRbacAccessControlService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ChannelCredentialCaseTransactionTest {
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void shouldCommitAfterAllPortsSucceed() {
        ChannelCredentialCaseService service = context.getBean(ChannelCredentialCaseService.class);
        TrackingTransactionManager manager = context.getBean(TrackingTransactionManager.class);

        service.replaceDataScopes(actor(), new ReplaceDataScopes(1L, "STORE", List.of("store-1")));

        assertEquals(1, manager.commits.get());
        assertEquals(0, manager.rollbacks.get());
    }

    @Test
    void shouldRollbackWhenRepositoryFails() {
        ChannelCredentialCaseService service = context.getBean(ChannelCredentialCaseService.class);
        IChannelDataScopeRepository repository = context.getBean(IChannelDataScopeRepository.class);
        TrackingTransactionManager manager = context.getBean(TrackingTransactionManager.class);
        doThrow(new IllegalStateException("写入失败")).when(repository)
                .replace(1L, "STORE", List.of("store-1"), 1L);

        assertThrows(IllegalStateException.class,
                () -> service.replaceDataScopes(actor(), new ReplaceDataScopes(1L, "STORE", List.of("store-1"))));
        assertEquals(0, manager.commits.get());
        assertEquals(1, manager.rollbacks.get());
    }

    private Actor actor() {
        return new Actor("request-1", 1L, 1L, "root", "ADMIN_PRIMARY", "gateway");
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        TrackingTransactionManager transactionManager() {
            return new TrackingTransactionManager();
        }

        @Bean
        IChannelCredentialDomainService domainService() {
            IChannelCredentialDomainService service = mock(IChannelCredentialDomainService.class);
            when(service.queryById(1L)).thenReturn(ChannelCredentialEntity.builder().id(1L).build());
            return service;
        }

        @Bean
        IRbacAccessControlService accessControlService() {
            return mock(IRbacAccessControlService.class);
        }

        @Bean
        IChannelDataScopeRepository dataScopeRepository() {
            IChannelDataScopeRepository repository = mock(IChannelDataScopeRepository.class);
            when(repository.findEnabledByChannelId(1L, "STORE")).thenReturn(List.of());
            return repository;
        }

        @Bean
        ChannelCredentialCaseService caseService(IChannelCredentialDomainService domainService,
                                                 IRbacAccessControlService accessControlService,
                                                 IChannelDataScopeRepository dataScopeRepository) {
            return new ChannelCredentialCaseService(domainService, accessControlService, dataScopeRepository);
        }
    }

    static final class TrackingTransactionManager extends AbstractPlatformTransactionManager {
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger rollbacks = new AtomicInteger();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // 测试事务管理器不持有外部资源，只记录代理的事务生命周期。
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
