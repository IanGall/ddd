package cn.iantech.redis;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 微服务共享的 Redis 能力契约。
 *
 * <p>接口只暴露业务可使用的值、字符串、Hash、有序集合和原子脚本能力，不泄漏 Redisson 类型。</p>
 */
public interface IRedisService {

    <T> void setValue(String key, T value);

    <T> void setValue(String key, T value, Duration ttl);

    <T> T getValue(String key);

    void remove(String key);

    boolean setIfAbsent(String key, Object value, Duration ttl);

    String getString(String key);

    Map<String, String> readStringHash(String key);

    Map<String, Map<String, String>> readStringHashes(List<String> keys);

    void removeSortedSetByScore(String key, double startScore, boolean startInclusive,
                                double endScore, boolean endInclusive);

    List<String> rangeSortedSetByScore(String key, double startScore, boolean startInclusive,
                                       double endScore, boolean endInclusive);

    Long executeLongScript(String script, List<String> keys, List<?> arguments);
}
