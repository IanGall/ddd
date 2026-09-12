package cn.iantech.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 启动安全校验：非本地环境（dev / test / autotest / local 之外的 profile）拒绝使用
 * 示例渠道主密钥或平台默认令牌启动，避免生产误用开发配置导致保护机制失效。
 */
@Component
public class SecretConfigurationValidator {

    /** 仅这些 Profile 允许使用本地开发便利值。 */
    private static final Set<String> LOCAL_PROFILES = Set.of("dev", "test", "autotest", "local");

    /** 已知弱值：全零 AES 主密钥（历史默认值）。 */
    private static final Set<String> WEAK_MASTER_KEYS = Set.of("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    /** 已知弱值：文档与配置中出现过的默认平台令牌。 */
    private static final Set<String> WEAK_ADMIN_TOKENS = Set.of("__REMOVED__", "__REMOVED__");

    private final Environment environment;
    private final String masterKey;
    private final String adminToken;

    public SecretConfigurationValidator(Environment environment,
            @Value("${channel.security.encryption.master-key:}") String masterKey,
            @Value("${platform.security.admin-token:}") String adminToken) {
        this.environment = environment;
        this.masterKey = masterKey;
        this.adminToken = adminToken;
    }

    @PostConstruct
    public void validate() {
        if (isLocalEnvironment()) {
            return;
        }
        if (WEAK_MASTER_KEYS.contains(masterKey)) {
            throw new IllegalStateException(
                    "检测到全零渠道主密钥：非本地环境必须注入独立的 CHANNEL_ENCRYPTION_MASTER_KEY");
        }
        if (adminToken.isBlank() || WEAK_ADMIN_TOKENS.contains(adminToken)) {
            throw new IllegalStateException(
                    "检测到空或示例平台令牌：非本地环境必须注入独立的 PLATFORM_ADMIN_TOKEN");
        }
    }

    private boolean isLocalEnvironment() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(LOCAL_PROFILES::contains);
    }
}
