package cn.iantech.id.autoconfigure;

import cn.iantech.id.IdGenerationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 全局 ID 生成器配置。
 *
 * <p>Worker ID 是**应用实例级**资源：一个实例从服务级池中租用一个独占的 Worker ID，
 * 实例内所有业务共用它。因此业务数量不受池容量限制，池容量只约束实例（副本）数量。
 *
 * <p>每个业务仍持有独立的生成器实例与独立的序列计数器，并可各自配置
 * {@code sequence-bit-length}（业务数量的唯一上限是配置项数量）。
 */
@ConfigurationProperties(prefix = "ddd.id-generator")
public class IdGeneratorProperties {

    /** Worker ID 位宽与序列位宽之和的上限：long 可用 63 位减去时间戳预留的 41 位。 */
    private static final int MAX_WORKER_AND_SEQUENCE_BIT_LENGTH = 22;
    private static final int MIN_WORKER_ID_BIT_LENGTH = 1;
    private static final int MAX_WORKER_ID_BIT_LENGTH = 15;
    private static final int MIN_SEQUENCE_BIT_LENGTH = 3;
    private static final int MAX_SEQUENCE_BIT_LENGTH = 21;

    /** 业务名只允许小写字母、数字与连字符。 */
    private static final Pattern BUSINESS_NAME = Pattern.compile("[a-z][a-z0-9-]*");

    private boolean enabled = true;

    /** 服务级 Redis 命名空间；为空时由自动装配从 {@code spring.application.name} 派生。 */
    private String namespace;

    private int workerIdBitLength = 10;
    private int sequenceBitLength = 12;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration renewInterval = Duration.ofSeconds(10);

    /** 业务名 → 该业务的序列位宽。声明了业务即启用多业务模式。 */
    private Map<String, Integer> businesses = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public int getWorkerIdBitLength() {
        return workerIdBitLength;
    }

    public void setWorkerIdBitLength(int workerIdBitLength) {
        this.workerIdBitLength = workerIdBitLength;
    }

    public int getSequenceBitLength() {
        return sequenceBitLength;
    }

    public void setSequenceBitLength(int sequenceBitLength) {
        this.sequenceBitLength = sequenceBitLength;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getRenewInterval() {
        return renewInterval;
    }

    public void setRenewInterval(Duration renewInterval) {
        this.renewInterval = renewInterval;
    }

    public Map<String, Integer> getBusinesses() {
        return businesses;
    }

    public void setBusinesses(Map<String, Integer> businesses) {
        this.businesses = businesses == null ? new LinkedHashMap<>() : new LinkedHashMap<>(businesses);
    }

    /**
     * 指定业务实际使用的序列位宽。
     *
     * @param business 业务名
     * @return 该业务声明的位宽；未声明或未给值时回退到全局 {@code sequence-bit-length}
     */
    public int sequenceBitLengthFor(String business) {
        Integer declared = businesses.get(business);
        return declared == null ? sequenceBitLength : declared;
    }

    /**
     * 本配置可提供的 WorkerId 总数，等于可并存的实例（副本）上限。
     *
     * @return 池容量，等于 {@code 2^workerIdBitLength}
     */
    public int workerPoolSize() {
        return 1 << workerIdBitLength;
    }

    /**
     * 校验配置。构造生成器前调用，让配置错误在启动期失败而不是等到出号。
     */
    public void validate() {
        validateNamespace();
        if (workerIdBitLength < MIN_WORKER_ID_BIT_LENGTH || workerIdBitLength > MAX_WORKER_ID_BIT_LENGTH) {
            throw new IdGenerationException("ddd.id-generator.worker-id-bit-length 必须在 1 到 15 之间");
        }
        if (sequenceBitLength < MIN_SEQUENCE_BIT_LENGTH || sequenceBitLength > MAX_SEQUENCE_BIT_LENGTH) {
            throw new IdGenerationException("ddd.id-generator.sequence-bit-length 必须在 3 到 21 之间");
        }
        validateBusinesses();
        validateLease();
    }

    private void validateNamespace() {
        if (namespace == null || namespace.isBlank() || !namespace.equals(namespace.trim())
                || namespace.indexOf('{') >= 0 || namespace.indexOf('}') >= 0
                || namespace.indexOf(':') >= 0
                || namespace.chars().anyMatch(Character::isWhitespace)) {
            throw new IdGenerationException("ddd.id-generator.namespace 非法");
        }
    }

    private void validateBusinesses() {
        if (businesses.isEmpty()) {
            if (workerIdBitLength + sequenceBitLength > MAX_WORKER_AND_SEQUENCE_BIT_LENGTH) {
                throw new IdGenerationException(
                        "worker-id-bit-length 与 sequence-bit-length 之和不得超过 22");
            }
            return;
        }
        for (Map.Entry<String, Integer> entry : businesses.entrySet()) {
            String business = entry.getKey();
            if (business == null || !BUSINESS_NAME.matcher(business).matches()) {
                throw new IdGenerationException(
                        "ddd.id-generator.businesses 的业务名非法：只允许小写字母、数字与连字符，且以字母开头");
            }
            Integer businessSequenceBitLength = entry.getValue();
            if (businessSequenceBitLength == null
                    || businessSequenceBitLength < MIN_SEQUENCE_BIT_LENGTH
                    || businessSequenceBitLength > MAX_SEQUENCE_BIT_LENGTH) {
                throw new IdGenerationException(
                        "业务 " + business + " 的 sequence-bit-length 必须在 3 到 21 之间");
            }
            if (workerIdBitLength + businessSequenceBitLength > MAX_WORKER_AND_SEQUENCE_BIT_LENGTH) {
                throw new IdGenerationException("业务 " + business
                        + " 的序列位长过大：worker-id-bit-length 与 sequence-bit-length 之和不得超过 22");
            }
        }
    }

    private void validateLease() {
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IdGenerationException("ddd.id-generator.lease-duration 必须大于 0");
        }
        if (renewInterval == null || renewInterval.isZero() || renewInterval.isNegative()
                || renewInterval.compareTo(leaseDuration) >= 0) {
            throw new IdGenerationException("ddd.id-generator.renew-interval 必须大于 0 且小于租约时长");
        }
        try {
            leaseDuration.toMillis();
            renewInterval.toMillis();
            leaseDuration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IdGenerationException("ID 生成器时间配置超出支持范围", exception);
        }
        if (leaseDuration.toMillis() <= 0 || renewInterval.toMillis() <= 0) {
            throw new IdGenerationException("ID 生成器时间配置不得小于 1 毫秒");
        }
    }
}
