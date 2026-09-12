package cn.iantech.id;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 全局 ID 生成器配置。
 */
@ConfigurationProperties(prefix = "ddd.id-generator")
public class IdGeneratorProperties {

    /** Yitter 全局 ID 的 Worker ID 位宽与序列位宽之和。 */
    private static final int TOTAL_BIT_LENGTH = 22;
    private static final int MIN_WORKER_ID_BIT_LENGTH = 1;
    private static final int MAX_WORKER_ID_BIT_LENGTH = 15;
    private static final int MIN_SEQUENCE_BIT_LENGTH = 3;
    private static final int MAX_SEQUENCE_BIT_LENGTH = 21;

    private boolean enabled = true;
    private String namespace = "ddd-global-id";
    private int workerIdBitLength = 10;
    private int sequenceBitLength = 12;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration renewInterval = Duration.ofSeconds(10);

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

    void validate() {
        if (namespace == null || namespace.isBlank() || !namespace.equals(namespace.trim())
                || namespace.indexOf('{') >= 0 || namespace.indexOf('}') >= 0
                || namespace.indexOf(':') >= 0
                || namespace.chars().anyMatch(Character::isWhitespace)) {
            throw new IdGenerationException("ddd.id-generator.namespace 非法");
        }
        if (workerIdBitLength < MIN_WORKER_ID_BIT_LENGTH || workerIdBitLength > MAX_WORKER_ID_BIT_LENGTH) {
            throw new IdGenerationException("ddd.id-generator.worker-id-bit-length 必须在 1 到 15 之间");
        }
        if (sequenceBitLength < MIN_SEQUENCE_BIT_LENGTH || sequenceBitLength > MAX_SEQUENCE_BIT_LENGTH) {
            throw new IdGenerationException("ddd.id-generator.sequence-bit-length 必须在 3 到 21 之间");
        }
        if (workerIdBitLength + sequenceBitLength != TOTAL_BIT_LENGTH) {
            throw new IdGenerationException("Worker ID 位数与序列位数之和必须等于 22");
        }
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

    int workerPoolSize() {
        return 1 << workerIdBitLength;
    }
}
