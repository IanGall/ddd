package cn.iantech.cases.customer.service;

import cn.iantech.cases.customer.model.CustomerRegisterCommand;
import cn.iantech.common.exception.AppException;
import cn.iantech.common.util.Sha256;
import cn.iantech.domain.auth.infra.IAuthRiskStore;
import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.auth.service.PasswordPolicy;
import cn.iantech.domain.customer.infra.ICustomerUserRepository;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

import static cn.iantech.common.constant.Constants.ResponseCode.AUTH_RATE_LIMITED;
import static cn.iantech.common.constant.Constants.ResponseCode.CONFLICT;
import static cn.iantech.common.constant.Constants.ResponseCode.INVALID_ARGUMENT;

/**
 * C 端用户注册用例。
 */
@Service
@RequiredArgsConstructor
public class CustomerCaseService {

    /** 注册入口按 IP 风控：单 IP 60 秒内最多 10 次注册尝试。注册远比登录低频，故额度低于登录的 30 次。 */
    private static final int MAXIMUM_REGISTER_ATTEMPTS = 10;
    private static final Duration REGISTER_ATTEMPT_WINDOW = Duration.ofSeconds(60);
    /**
     * 风控计数命名空间：注册与登录各自计数，互不消耗对方的按 IP 配额，
     * 避免 NAT 场景下注册流量把同 IP 的登录额度挤掉。
     */
    private static final String REGISTER_RISK_NAMESPACE = "register:";
    /** 客户端地址缺失时的占位值（与登录风控一致）：网关正常路径总会填充真实地址。 */
    private static final String UNKNOWN_CLIENT_ADDRESS = "unknown";

    private final ICustomerUserRepository repository;
    private final IPasswordEncoder passwordEncoder;
    private final IAuthRiskStore riskStore;

    /**
     * C 端注册。
     *
     * <p>刻意**不加** {@code @Transactional}：本方法只有一次 INSERT，单语句自身即原子，
     * 而 BCrypt 慢哈希（约 100ms）若处在事务内会一直占用连接池连接——匿名注册是唯一
     * 无需认证即可触发的入口，长事务会被少量并发直接放大为连接池耗尽。
     * 登录名重复由 {@code uk_customer_user_login_name} 唯一索引兜底（见仓储实现）。</p>
     *
     * <p>风控检查置于入参校验之前：与登录路径一致，任何一次尝试都计入额度，
     * 使畸形请求的洪泛同样受限。</p>
     */
    public CustomerUserEntity register(CustomerRegisterCommand command) {
        if (command == null) {
            throw new AppException(INVALID_ARGUMENT.getCode(), "请求不能为空");
        }
        if (!riskStore.allowIpAttempt(registerRiskKey(command.ipAddress()),
                MAXIMUM_REGISTER_ATTEMPTS, REGISTER_ATTEMPT_WINDOW)) {
            throw new AppException(AUTH_RATE_LIMITED.getCode(), "注册请求过于频繁，请稍后重试");
        }
        String normalized = normalizeLoginName(command.loginName());
        PasswordPolicy.check(command.password());
        if (repository.findByLoginName(normalized).isPresent()) {
            throw new AppException(CONFLICT.getCode(), "登录账号已注册");
        }
        return repository.save(CustomerUserEntity.builder().loginName(normalized)
                .passwordHash(passwordEncoder.encode(command.password()))
                .displayName(command.displayName() == null ? "" : command.displayName())
                .status(true).deleted(false).build());
    }

    /**
     * 风控存储只接受不可逆摘要，原始 IP 不落 Redis。
     */
    private String registerRiskKey(String ipAddress) {
        return Sha256.hex(REGISTER_RISK_NAMESPACE + StringUtils.defaultIfBlank(ipAddress, UNKNOWN_CLIENT_ADDRESS));
    }

    private String normalizeLoginName(String value) {
        if (value == null) {
            throw new AppException(INVALID_ARGUMENT.getCode(), "登录账号不合法");
        }
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9_.-]{1,64}")) {
            throw new AppException(INVALID_ARGUMENT.getCode(), "登录账号不合法");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
