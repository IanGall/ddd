package cn.iantech.id.core;

import cn.iantech.id.IdGenerationException;
import cn.iantech.id.autoconfigure.IdGeneratorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    private static final int WORKER_ID_BIT_LENGTH = 10;
    private static final int SEQUENCE_BIT_LENGTH = 12;
    private static final int SHIFT = WORKER_ID_BIT_LENGTH + SEQUENCE_BIT_LENGTH;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BIT_LENGTH) - 1;

    /** 相对 BASE_TIME 的毫秒刻度，便于用常量断言 tick 位。 */
    private static final long INITIAL_TICK = 1_000_000L;

    private AtomicLong clock;

    @BeforeEach
    void setUp() {
        clock = new AtomicLong(SnowflakeIdGenerator.BASE_TIME + INITIAL_TICK);
    }

    @Test
    void shouldComposeIdWithConfiguredBitLayout() {
        SnowflakeIdGenerator generator = generator(7);

        long id = generator.nextId();

        assertThat(id).isEqualTo((INITIAL_TICK << SHIFT)
                + (7L << SEQUENCE_BIT_LENGTH) + SnowflakeIdGenerator.MIN_SEQUENCE_NUMBER);
    }

    @Test
    void shouldIncrementSequenceWithinSameMillisecond() {
        SnowflakeIdGenerator generator = generator(0);

        long first = generator.nextId();
        long second = generator.nextId();
        long third = generator.nextId();

        assertThat(second).isEqualTo(first + 1);
        assertThat(third).isEqualTo(first + 2);
    }

    @Test
    void shouldRestartSequenceWhenClockAdvances() {
        SnowflakeIdGenerator generator = generator(0);
        long first = generator.nextId();

        clock.addAndGet(1L);

        long nextMillisecond = generator.nextId();
        assertThat(nextMillisecond - first).isEqualTo(1L << SHIFT);
        assertThat(tickOf(nextMillisecond)).isEqualTo(INITIAL_TICK + 1);
        assertThat(sequenceOf(nextMillisecond)).isEqualTo(SnowflakeIdGenerator.MIN_SEQUENCE_NUMBER);
        assertThat(sequenceOf(generator.nextId())).isEqualTo(SnowflakeIdGenerator.MIN_SEQUENCE_NUMBER + 1);
    }

    @Test
    void shouldBorrowNextTickWhenSequenceOverflows() {
        SnowflakeIdGenerator generator = narrowRangeGenerator(10);

        long first = generator.nextId();
        assertThat(tickOf(first, 3)).isEqualTo(INITIAL_TICK);
        assertThat(sequenceOf(first, 3)).isEqualTo(5);
        assertThat(sequenceOf(generator.nextId(), 3)).isEqualTo(6);
        assertThat(sequenceOf(generator.nextId(), 3)).isEqualTo(7);

        long borrowed = generator.nextId();

        assertThat(tickOf(borrowed, 3)).isEqualTo(INITIAL_TICK + 1);
        assertThat(sequenceOf(borrowed, 3)).isEqualTo(5);
    }

    @Test
    void shouldKeepIdsDistinctAndIncreasingWhileDrifting() {
        SnowflakeIdGenerator generator = narrowRangeGenerator(10);

        // 漂移上限 10：首个毫秒 3 个 ID，随后 9 个借用的毫秒各 3 个 ID，第 10 个借用毫秒只能出 1 个 ID。
        List<Long> ids = IntStream.range(0, 31).mapToObj(ignored -> generator.nextId()).toList();

        assertThat(ids).doesNotHaveDuplicates().isSorted();
        assertThat(tickOf(ids.get(30), 3)).isEqualTo(INITIAL_TICK + 10);
    }

    @Test
    void shouldExitOverCostWhenClockCatchesUp() {
        SnowflakeIdGenerator generator = narrowRangeGenerator(10);
        for (int index = 0; index < 4; index++) {
            generator.nextId();
        }

        clock.set(SnowflakeIdGenerator.BASE_TIME + INITIAL_TICK + 50L);

        assertThat(tickOf(generator.nextId(), 3)).isEqualTo(INITIAL_TICK + 50L);
    }

    @Test
    void shouldFailClosedWhenClockNeverAdvancesPastOverCostLimit() {
        SnowflakeIdGenerator generator = narrowRangeGenerator(2);

        assertThatThrownBy(() -> IntStream.range(0, 100).forEach(ignored -> generator.nextId()))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("等待时钟前进超时");
    }

    @Test
    void shouldEmitReservedTurnBackSequenceWhenClockMovesBackward() {
        SnowflakeIdGenerator generator = generator(0);
        generator.nextId();

        clock.addAndGet(-2L);
        long firstTurnBack = generator.nextId();
        long secondTurnBack = generator.nextId();

        assertThat(tickOf(firstTurnBack)).isEqualTo(INITIAL_TICK - 1);
        assertThat(sequenceOf(firstTurnBack)).isEqualTo(1);
        assertThat(tickOf(secondTurnBack)).isEqualTo(INITIAL_TICK - 2);
        assertThat(sequenceOf(secondTurnBack)).isEqualTo(1);
        assertThat(firstTurnBack).isNotEqualTo(secondTurnBack);
    }

    @Test
    void shouldClearTurnBackStateWhenClockCatchesUp() {
        SnowflakeIdGenerator generator = generator(0);
        generator.nextId();
        clock.addAndGet(-2L);
        generator.nextId();

        clock.set(SnowflakeIdGenerator.BASE_TIME + INITIAL_TICK);
        long caughtUp = generator.nextId();

        assertThat(tickOf(caughtUp)).isEqualTo(INITIAL_TICK);
        assertThat(sequenceOf(caughtUp)).isEqualTo(SnowflakeIdGenerator.MIN_SEQUENCE_NUMBER + 1);
    }

    @Test
    void shouldAdvanceTurnBackIndexPerEpisodeAndWrapAfterFour() {
        SnowflakeIdGenerator generator = generator(0);

        assertThat(turnBackSequences(generator, 5)).containsExactly(1L, 2L, 3L, 4L, 1L);
    }

    @Test
    void shouldIsolateSequenceStateBetweenInstances() {
        SnowflakeIdGenerator first = generator(7);
        SnowflakeIdGenerator second = generator(9);

        long firstOfFirst = first.nextId();
        long firstOfSecond = second.nextId();
        long secondOfFirst = first.nextId();
        long secondOfSecond = second.nextId();

        assertThat(tickOf(firstOfFirst)).isEqualTo(tickOf(firstOfSecond)).isEqualTo(INITIAL_TICK);
        assertThat(firstOfFirst).isNotEqualTo(firstOfSecond);
        assertThat(secondOfFirst).isEqualTo(firstOfFirst + 1);
        assertThat(secondOfSecond).isEqualTo(firstOfSecond + 1);
    }

    @Test
    void shouldGenerateUniqueIdsConcurrently() throws InterruptedException {
        SnowflakeIdGenerator generator = generator(0);
        int threadCount = 8;
        int idsPerThread = 2_000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            for (int index = 0; index < threadCount; index++) {
                executor.execute(() -> {
                    awaitQuietly(start);
                    for (int count = 0; count < idsPerThread; count++) {
                        ids.add(generator.nextId());
                    }
                });
            }
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(awaitTermination(executor)).isTrue();
        }

        assertThat(ids).hasSize(threadCount * idsPerThread);
    }

    @Test
    void shouldRejectWorkerIdOutsideBitRange() {
        assertThatThrownBy(() -> generator(1 << WORKER_ID_BIT_LENGTH))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("Worker ID 超出位宽范围");
        assertThatThrownBy(() -> generator(-1))
                .isInstanceOf(IdGenerationException.class)
                .hasMessageContaining("Worker ID 超出位宽范围");
    }

    private SnowflakeIdGenerator generator(int workerId) {
        return new SnowflakeIdGenerator(workerId, WORKER_ID_BIT_LENGTH, SEQUENCE_BIT_LENGTH,
                SnowflakeIdGenerator.TOP_OVER_COST_COUNT, clock::get);
    }

    /** 序列位长 3（每毫秒仅 5/6/7 三个序列值），便于快速触发漂移。 */
    private SnowflakeIdGenerator narrowRangeGenerator(int topOverCostCount) {
        return new SnowflakeIdGenerator(0, WORKER_ID_BIT_LENGTH, 3, topOverCostCount, clock::get);
    }

    /** 依次制造 5 次时钟回拨并各自追平，收集每次回拨所用的序列次序位。 */
    private List<Long> turnBackSequences(SnowflakeIdGenerator generator, int episodes) {
        generator.nextId();
        return IntStream.range(0, episodes).mapToObj(ignored -> {
            clock.addAndGet(-2L);
            long turnBack = generator.nextId();
            clock.set(SnowflakeIdGenerator.BASE_TIME + INITIAL_TICK);
            generator.nextId();
            return sequenceOf(turnBack);
        }).toList();
    }

    private long tickOf(long id) {
        return id >>> SHIFT;
    }

    private long tickOf(long id, int sequenceBitLength) {
        return id >>> (WORKER_ID_BIT_LENGTH + sequenceBitLength);
    }

    private long sequenceOf(long id) {
        return id & SEQUENCE_MASK;
    }

    private long sequenceOf(long id, int sequenceBitLength) {
        return id & ((1L << sequenceBitLength) - 1);
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean awaitTermination(ExecutorService executor) {
        try {
            return executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
