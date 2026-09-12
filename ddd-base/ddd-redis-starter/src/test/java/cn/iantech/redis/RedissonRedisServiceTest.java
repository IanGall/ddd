package cn.iantech.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class RedissonRedisServiceTest {

    private RedissonClient redissonClient;
    private RedissonRedisService redisService;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        redisService = new RedissonRedisService(redissonClient);
    }

    @Test
    void shouldReadAndWriteOrdinaryValue() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket("value")).thenReturn(bucket);
        when(bucket.get()).thenReturn("stored");

        redisService.setValue("value", "stored");
        redisService.setValue("value", "stored", Duration.ofMinutes(1));

        verify(bucket).set("stored");
        verify(bucket).set("stored", Duration.ofMinutes(1));
        assertThat(redisService.<String>getValue("value")).isEqualTo("stored");
    }

    @Test
    void shouldSetValueIfAbsentWithTtl() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket("replay")).thenReturn(bucket);
        when(bucket.setIfAbsent("1", Duration.ofSeconds(30))).thenReturn(true);

        boolean created = redisService.setIfAbsent("replay", "1", Duration.ofSeconds(30));

        assertThat(created).isTrue();
    }

    @Test
    void shouldUseStringCodecForStringAndHash() {
        RBucket<String> bucket = mock(RBucket.class);
        RMap<String, String> hash = mock(RMap.class);
        when(redissonClient.<String>getBucket("string", StringCodec.INSTANCE)).thenReturn(bucket);
        when(bucket.get()).thenReturn("value");
        when(redissonClient.<String, String>getMap("hash", StringCodec.INSTANCE)).thenReturn(hash);
        when(hash.readAllMap()).thenReturn(Map.of("field", "value"));

        assertThat(redisService.getString("string")).isEqualTo("value");
        assertThat(redisService.readStringHash("hash")).containsEntry("field", "value");
    }

    @Test
    void shouldReadHashesInOneBatchAndKeepInputOrder() {
        RBatch batch = mock(RBatch.class);
        RMapAsync<String, String> first = mock(RMapAsync.class);
        RMapAsync<String, String> second = mock(RMapAsync.class);
        when(redissonClient.createBatch()).thenReturn(batch);
        when(batch.<String, String>getMap("first", StringCodec.INSTANCE)).thenReturn(first);
        when(batch.<String, String>getMap("second", StringCodec.INSTANCE)).thenReturn(second);
        doReturn(new BatchResult<>(List.of(Map.of("id", "1"), Map.of("id", "2")), 0))
                .when(batch).execute();

        Map<String, Map<String, String>> values = redisService.readStringHashes(List.of("first", "second"));

        verify(first).readAllMapAsync();
        verify(second).readAllMapAsync();
        assertThat(values.keySet()).containsExactly("first", "second");
        assertThat(values.get("second")).containsEntry("id", "2");
    }

    @Test
    void shouldOperateSortedSetWithStringCodec() {
        RScoredSortedSet<String> sortedSet = mock(RScoredSortedSet.class);
        when(redissonClient.<String>getScoredSortedSet("sessions", StringCodec.INSTANCE)).thenReturn(sortedSet);
        when(sortedSet.valueRange(10, false, 20, true)).thenReturn(List.of("s1", "s2"));

        redisService.removeSortedSetByScore("sessions", 0, true, 10, true);
        List<String> values = redisService.rangeSortedSetByScore("sessions", 10, false, 20, true);

        verify(sortedSet).removeRangeByScore(0, true, 10, true);
        assertThat(values).containsExactly("s1", "s2");
    }

    @Test
    void shouldExecuteWriteScriptWithStringCodecAndLongResult() {
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(eq(RScript.Mode.READ_WRITE), eq("return 1"), eq(RScript.ReturnType.LONG),
                anyList(), any(Object[].class))).thenReturn(1L);

        Long result = redisService.executeLongScript("return 1", List.of("key"), List.of("argument"));

        assertThat(result).isEqualTo(1L);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), eq("return 1"), eq(RScript.ReturnType.LONG),
                eq(List.of("key")), eq(new Object[]{"argument"}));
    }
}
