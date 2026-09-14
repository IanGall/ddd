package cn.iantech.id.autoconfigure;

import cn.iantech.id.IdGenerationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 全局 ID 生成器配置。
 */
@ConfigurationProperties(prefix = "ddd.id-generator")
public class IdGeneratorProperties {

    /** 全局 ID 的 Worker ID 位宽与序列位宽之和。 */
    private static final int TOTAL_BIT_LENGTH = 22;
    private static final int MIN_WORKER_ID_BIT_LENGTH = 1;
    private static final int MAX_WORKER_ID_BIT_LENGTH = 15;
    private static final int MIN_SEQUENCE_BIT_LENGTH = 3;
    private static final int MAX_SEQUENCE_BIT_LENGTH = 21;

    /** 业务名只允许小写字母、数字与连字符。 */
    private static final Pattern BUSINESS_NAME = Pattern.compile("[a-z][a-z0-9-]*");

    private boolean enabled = true;
    private String namespace = "ddd-global-id";
    private int workerIdBitLength = 10;
    private int sequenceBitLength = 12;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration renewInterval = Duration.ofSeconds(10);
    private int workerIdBlockSize = 64;
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

    public int getWorkerIdBlockSize() {
        return workerIdBlockSize;
    }

    public void setWorkerIdBlockSize(int workerIdBlockSize) {
        this.workerIdBlockSize = workerIdBlockSize;
    }

    public Map<String, Integer> getBusinesses() {
        return businesses;
    }

    public void setBusinesses(Map<String, Integer> businesses) {
        this.businesses = businesses == null ? new LinkedHashMap<>() : new LinkedHashMap<>(businesses);
    }

    /**
     * 业务块序号对应的 Worker ID 起始值。
     *
     * @param businessIndex 块序号，必须已通过 {@link #validate()}
     * @return 该业务可用 WorkerId 的起始值（含）
     */
    public int blockStart(int businessIndex) {
        return businessIndex * workerIdBlockSize;
    }

    /**
     * 校验配置。构造生成器前调用，让配置错误在启动期失败而不是等到出号。
     */
    public void validate() {
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
        validateBusinesses();
    }

    private void validateBusinesses() {
        if (workerIdBlockSize <= 0) {
            throw new IdGenerationException("ddd.id-generator.worker-id-block-size 必须大于 0");
        }
        if (businesses.isEmpty()) {
            return;
        }
        int maxIndex = -1;
        Set<Integer> usedIndices = new HashSet<>();
        for (Map.Entry<String, Integer> entry : businesses.entrySet()) {
            String business = entry.getKey();
            if (business == null || !BUSINESS_NAME.matcher(business).matches()) {
                throw new IdGenerationException(
                        "ddd.id-generator.businesses 的业务名非法：只允许小写字母、数字与连字符，且以字母开头");
            }
            Integer index = entry.getValue();
            if (index == null || index < 0) {
                throw new IdGenerationException(
                        "ddd.id-generator.businesses." + business + " 的 block 序号不能为负");
            }
            if (!usedIndices.add(index)) {
                throw new IdGenerationException("ddd.id-generator.businesses 的 block 序号重复：" + index);
            }
            maxIndex = Math.max(maxIndex, index);
        }
        long required = (maxIndex + 1L) * workerIdBlockSize;
        if (required > workerPoolSize()) {
            throw new IdGenerationException("业务 Worker ID 区间超出池容量：需要 " + required
                    + " 个，实际只有 " + workerPoolSize() + " 个（worker-id-block-size=" + workerIdBlockSize
                    + "，最大 block 序号=" + maxIndex + "）");
        }
    }

    /**
     * 本配置可提供的 WorkerId 总数。
     *
     * @return 池容量，等于 {@code 2^workerIdBitLength}
     */
    public int workerPoolSize() {
        return 1 << workerIdBitLength;
    }
}
