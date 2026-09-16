package cn.iantech.id.core;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.IdGenerationException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 雪花漂移算法的单业务实例实现。
 *
 * <p>ID 布局（与历史实现逐位一致，保证与已入库 ID 的大小与排序兼容）：
 * <pre>
 * id = ((当前毫秒 - BASE_TIME) &lt;&lt; (workerIdBitLength + sequenceBitLength))
 *      + (workerId &lt;&lt; sequenceBitLength) + 序列号
 * </pre>
 *
 * <p>每个业务实例持有独立的锁与序列状态，因此同一 JVM 内可为不同业务各建一个实例。
 * 但它们传入的是**同一个实例级 Worker ID**（由 {@link WorkerIdLeaseHolder} 独占），
 * 只有序列位宽可以各自不同；因此跨业务的 ID 会重复，唯一性只在业务域内成立。
 *
 * <p>与参考实现（yitter 1.0.6）的两处刻意差异：
 * <ol>
 *   <li>等待时钟前进由无界忙等改为有界自旋：时钟被大幅回拨时不再烧 CPU，超时直接失败关闭。</li>
 *   <li>序列号使用 int 而非 short：位宽较大（序列位长 &gt; 15）时短路会溢出并导致序列判定失效。</li>
 * </ol>
 */
final class SnowflakeIdGenerator implements GlobalIdGenerator {

    /** 基础时间（毫秒），沿用历史值以保持 ID 的大小可比性。 */
    static final long BASE_TIME = 1582136402000L;

    /** 每毫秒序列数的前 5 个值预留给时间回拨次序与人工新值。 */
    static final int MIN_SEQUENCE_NUMBER = 5;

    /** 单次漂移最多借用的未来毫秒数。 */
    static final int TOP_OVER_COST_COUNT = 2000;

    /** 等待时钟前进的上限，超时即失败关闭。 */
    private static final Duration WAIT_CLOCK_LIMIT = Duration.ofSeconds(1);

    private final int workerId;
    private final int sequenceBitLength;
    private final int timestampShift;
    private final int maxSequenceNumber;
    private final int topOverCostCount;
    private final LongSupplier millis;

    /** 实例私有锁，绝不可为 static。 */
    private final Object lock = new Object();

    private int currentSequenceNumber = MIN_SEQUENCE_NUMBER;
    private long lastTimeTick;
    private long turnBackTimeTick;
    private int turnBackIndex;
    private boolean overCost;
    private int overCostCountInOneTerm;

    SnowflakeIdGenerator(int workerId, int workerIdBitLength, int sequenceBitLength,
                         int topOverCostCount, LongSupplier millis) {
        int workerCapacity = 1 << workerIdBitLength;
        if (workerId < 0 || workerId >= workerCapacity) {
            throw new IdGenerationException(
                    "Worker ID 超出位宽范围：[0, " + workerCapacity + ")，实际 " + workerId);
        }
        this.workerId = workerId;
        this.sequenceBitLength = sequenceBitLength;
        this.timestampShift = workerIdBitLength + sequenceBitLength;
        this.maxSequenceNumber = (1 << sequenceBitLength) - 1;
        this.topOverCostCount = topOverCostCount;
        this.millis = Objects.requireNonNull(millis, "毫秒时钟不能为空");
    }

    /**
     * 用共享的 Worker ID 与指定的位宽构造生成器。
     *
     * <p>Worker ID 由应用实例级租约独占，同一实例内的多个业务生成器传入同一个 {@code workerId}，
     * 但可以各自指定 {@code sequenceBitLength}（位宽之和不得超过 22）。
     */
    static SnowflakeIdGenerator of(int workerId, int workerIdBitLength, int sequenceBitLength) {
        return new SnowflakeIdGenerator(workerId, workerIdBitLength, sequenceBitLength,
                TOP_OVER_COST_COUNT, System::currentTimeMillis);
    }

    @Override
    public long nextId() {
        synchronized (lock) {
            return overCost ? nextOverCostId() : nextNormalId();
        }
    }

    private long nextNormalId() {
        long currentTimeTick = currentTimeTick();

        if (currentTimeTick < lastTimeTick) {
            // 时钟回拨：借 lastTimeTick-1 这一毫秒的预留序列位出号，每轮换一个次序位。
            if (turnBackTimeTick < 1) {
                turnBackTimeTick = lastTimeTick - 1;
                turnBackIndex++;
                // 每毫秒序列数的前 4 个值是回拨次序位；超过 4 次会与第 1 次重号，属已知局限。
                if (turnBackIndex > 4) {
                    turnBackIndex = 1;
                }
            }
            return calcTurnBackId(turnBackTimeTick);
        }

        if (turnBackTimeTick > 0) {
            turnBackTimeTick = 0;
        }

        if (currentTimeTick > lastTimeTick) {
            lastTimeTick = currentTimeTick;
            currentSequenceNumber = MIN_SEQUENCE_NUMBER;
            return calcId(lastTimeTick);
        }

        if (currentSequenceNumber > maxSequenceNumber) {
            // 本毫秒序列用尽：借下一毫秒并进入漂移，保证单调递增。
            lastTimeTick++;
            currentSequenceNumber = MIN_SEQUENCE_NUMBER;
            overCost = true;
            overCostCountInOneTerm = 1;
            return calcId(lastTimeTick);
        }

        return calcId(lastTimeTick);
    }

    private long nextOverCostId() {
        long currentTimeTick = currentTimeTick();

        if (currentTimeTick > lastTimeTick) {
            lastTimeTick = currentTimeTick;
            currentSequenceNumber = MIN_SEQUENCE_NUMBER;
            overCost = false;
            overCostCountInOneTerm = 0;
            return calcId(lastTimeTick);
        }

        if (overCostCountInOneTerm >= topOverCostCount) {
            // 漂移次数达到上限：必须等到真实时钟追上，否则继续借未来时间会越漂越远。
            lastTimeTick = nextTimeTick();
            currentSequenceNumber = MIN_SEQUENCE_NUMBER;
            overCost = false;
            overCostCountInOneTerm = 0;
            return calcId(lastTimeTick);
        }

        if (currentSequenceNumber > maxSequenceNumber) {
            lastTimeTick++;
            currentSequenceNumber = MIN_SEQUENCE_NUMBER;
            overCostCountInOneTerm++;
            return calcId(lastTimeTick);
        }

        return calcId(lastTimeTick);
    }

    private long calcId(long timeTick) {
        long id = (timeTick << timestampShift) + ((long) workerId << sequenceBitLength)
                + currentSequenceNumber;
        currentSequenceNumber++;
        return id;
    }

    private long calcTurnBackId(long timeTick) {
        long id = (timeTick << timestampShift) + ((long) workerId << sequenceBitLength) + turnBackIndex;
        turnBackTimeTick--;
        return id;
    }

    private long currentTimeTick() {
        return millis.getAsLong() - BASE_TIME;
    }

    private long nextTimeTick() {
        long deadlineNanos = System.nanoTime() + WAIT_CLOCK_LIMIT.toNanos();
        long timeTick = currentTimeTick();
        while (timeTick <= lastTimeTick) {
            if (System.nanoTime() - deadlineNanos >= 0) {
                throw new IdGenerationException("等待时钟前进超时，拒绝生成 ID");
            }
            timeTick = currentTimeTick();
        }
        return timeTick;
    }
}
