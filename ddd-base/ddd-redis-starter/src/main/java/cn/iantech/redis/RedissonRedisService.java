package cn.iantech.redis;

import org.redisson.api.BatchResult;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * 基于 Redisson 的公共 Redis 服务实现。
 */
public final class RedissonRedisService implements IRedisService {

    private final RedissonClient redissonClient;

    public RedissonRedisService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public <T> void setValue(String key, T value) {
        redissonClient.<T>getBucket(key).set(value);
    }

    @Override
    public <T> void setValue(String key, T value, Duration ttl) {
        redissonClient.<T>getBucket(key).set(value, ttl);
    }

    @Override
    public <T> T getValue(String key) {
        return redissonClient.<T>getBucket(key).get();
    }

    @Override
    public void remove(String key) {
        redissonClient.getBucket(key).delete();
    }

    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        return bucket.setIfAbsent(value, ttl);
    }

    @Override
    public String getString(String key) {
        return redissonClient.<String>getBucket(key, StringCodec.INSTANCE).get();
    }

    @Override
    public Map<String, String> readStringHash(String key) {
        return redissonClient.<String, String>getMap(key, StringCodec.INSTANCE).readAllMap();
    }

    @Override
    public Map<String, Map<String, String>> readStringHashes(List<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        RBatch batch = redissonClient.createBatch();
        keys.forEach(key -> batch.<String, String>getMap(key, StringCodec.INSTANCE).readAllMapAsync());
        BatchResult<?> result = batch.execute();
        List<?> responses = result.getResponses();
        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        IntStream.range(0, keys.size()).forEach(index -> values.put(keys.get(index), stringMap(responses.get(index))));
        return values;
    }

    @Override
    public void removeSortedSetByScore(String key, double startScore, boolean startInclusive,
                                       double endScore, boolean endInclusive) {
        redissonClient.<String>getScoredSortedSet(key, StringCodec.INSTANCE)
                .removeRangeByScore(startScore, startInclusive, endScore, endInclusive);
    }

    @Override
    public List<String> rangeSortedSetByScore(String key, double startScore, boolean startInclusive,
                                              double endScore, boolean endInclusive) {
        return redissonClient.<String>getScoredSortedSet(key, StringCodec.INSTANCE)
                .valueRange(startScore, startInclusive, endScore, endInclusive).stream().toList();
    }

    @Override
    public Long executeLongScript(String script, List<String> keys, List<?> arguments) {
        List<Object> scriptKeys = keys.stream().map(Object.class::cast).toList();
        return redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, script, RScript.ReturnType.LONG, scriptKeys, arguments.toArray());
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMap(Object response) {
        return (Map<String, String>) response;
    }
}
