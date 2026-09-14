package cn.iantech.cases.customer.service;

import cn.iantech.cases.customer.model.CustomerRegisterCommand;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.common.util.Sha256;
import cn.iantech.domain.auth.infra.IAuthRiskStore;
import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.customer.infra.ICustomerUserRepository;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册入口按 IP 风控：额度内放行、超额拒绝且不落库，并验证计数键使用独立命名空间。
 */
class CustomerCaseServiceTest {

    private static final String CLIENT_IP = "10.0.0.1";

    private final FakeCustomerUserRepository repository = new FakeCustomerUserRepository();
    private final FakeRiskStore riskStore = new FakeRiskStore();
    private final CustomerCaseService service =
            new CustomerCaseService(repository, new PrefixPasswordEncoder(), riskStore);

    @Test
    void shouldAllowRegistrationWithinIpLimit() {
        CustomerUserEntity saved = service.register(command("13800000000", CLIENT_IP));

        assertEquals("13800000000", saved.getLoginName());
        assertEquals("encoded:pwd-1234", saved.getPasswordHash());
        assertEquals(List.of(Sha256.hex("register:" + CLIENT_IP)), riskStore.attemptedKeys);
    }

    @Test
    void shouldRejectAndNotPersistWhenIpLimitExceeded() {
        riskStore.maximumAttempts = 1;
        service.register(command("13800000001", CLIENT_IP));

        AppException exception = assertThrows(AppException.class,
                () -> service.register(command("13800000002", CLIENT_IP)));

        assertEquals(Constants.ResponseCode.AUTH_RATE_LIMITED.getCode(), exception.getCode());
        assertEquals(List.of("13800000001"), repository.savedLoginNames);
    }

    @Test
    void shouldKeepRegisterCounterSeparateFromLoginCounter() {
        service.register(command("13800000003", CLIENT_IP));

        assertNotEquals(Sha256.hex(CLIENT_IP), riskStore.attemptedKeys.getFirst(),
                "注册风控必须与登录风控分桶，否则注册流量会挤占同 IP 的登录额度");
    }

    @Test
    void shouldFallBackToSharedBucketWhenClientAddressMissing() {
        service.register(command("13800000004", null));
        service.register(command("13800000005", "  "));

        assertEquals(List.of(Sha256.hex("register:unknown"), Sha256.hex("register:unknown")),
                riskStore.attemptedKeys);
    }

    @Test
    void shouldRejectNullCommandBeforeTouchingRiskStoreOrRepository() {
        assertThrows(AppException.class, () -> service.register(null));

        assertTrue(riskStore.attemptedKeys.isEmpty());
        assertTrue(repository.savedLoginNames.isEmpty());
    }

    private CustomerRegisterCommand command(String loginName, String ipAddress) {
        return new CustomerRegisterCommand(loginName, "pwd-1234", "C 端用户", ipAddress);
    }

    /**
     * 按 key 计数的风控替身：用于验证额度判定与计数键，并记录收到的 key。
     */
    private static final class FakeRiskStore implements IAuthRiskStore {

        private final Map<String, Integer> attempts = new HashMap<>();
        private final List<String> attemptedKeys = new ArrayList<>();
        private int maximumAttempts = Integer.MAX_VALUE;

        @Override
        public boolean allowIpAttempt(String ipAddressHash, int maximumAttempts, Duration window) {
            attemptedKeys.add(ipAddressHash);
            int current = attempts.merge(ipAddressHash, 1, Integer::sum);
            return current <= Math.min(maximumAttempts, this.maximumAttempts);
        }

        @Override
        public boolean isLoginBlocked(String loginNameHash, int maximumFailures) {
            return false;
        }

        @Override
        public boolean recordLoginFailure(String loginNameHash, int maximumFailures, Duration lockDuration) {
            return false;
        }

        @Override
        public void clearLoginFailures(String loginNameHash) {
            // 注册用例不涉及登录失败计数
        }
    }

    private static final class FakeCustomerUserRepository implements ICustomerUserRepository {

        private final Map<String, CustomerUserEntity> users = new LinkedHashMap<>();
        private final List<String> savedLoginNames = new ArrayList<>();

        @Override
        public CustomerUserEntity save(CustomerUserEntity entity) {
            CustomerUserEntity stored = CustomerUserEntity.builder()
                    .id((long) users.size() + 1)
                    .loginName(entity.getLoginName())
                    .passwordHash(entity.getPasswordHash())
                    .displayName(entity.getDisplayName())
                    .status(entity.getStatus())
                    .deleted(entity.getDeleted())
                    .build();
            users.put(stored.getLoginName(), stored);
            savedLoginNames.add(stored.getLoginName());
            return stored;
        }

        @Override
        public Optional<CustomerUserEntity> findByLoginName(String loginName) {
            return Optional.ofNullable(users.get(loginName));
        }

        @Override
        public Optional<CustomerUserEntity> findById(Long id) {
            return users.values().stream().filter(user -> user.getId().equals(id)).findFirst();
        }

        @Override
        public void updateLastLoginAt(Long id) {
            // 注册用例不涉及
        }

        @Override
        public void updatePassword(Long id, String passwordHash) {
            // 注册用例不涉及
        }
    }

    private static final class PrefixPasswordEncoder implements IPasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String passwordHash) {
            return passwordHash != null && passwordHash.equals("encoded:" + rawPassword);
        }
    }
}
