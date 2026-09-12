package cn.iantech.redis;

/**
 * Redis Cluster 用户 Key 生成器。
 *
 * <p>只有确实需要同用户同槽的 Key 才应使用该生成器。外层花括号是 Redis
 * 原生 Hash Tag 语法，业务代码不得自行拼接或替换。</p>
 */
public final class RedisKeyBuilder {

    private RedisKeyBuilder() {
    }

    /**
     * 创建用户 Key 作用域。
     */
    public static RedisKeyScope scope(long userId) {
        return new RedisKeyScope(userId);
    }

    /**
     * 返回作用域的 Redis Cluster Hash Tag。
     */
    public static String hashTag(RedisKeyScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope 不能为空");
        }
        return scope.hashTag();
    }

    /**
     * 生成带用户 Hash Tag 的 Redis Key。
     *
     * @param namespace  命名空间，可使用冒号表示层级
     * @param scope      用户作用域
     * @param resource   资源名称
     * @param identifier 资源标识
     * @return Redis Key
     */
    public static String key(String namespace, RedisKeyScope scope, String resource, String identifier) {
        validateSegment(namespace, "namespace", true);
        validateSegment(resource, "resource", false);
        validateSegment(identifier, "identifier", false);
        return scopedPrefix(namespace, scope) + ":" + resource + ":" + identifier;
    }

    /**
     * 生成不绑定用户作用域的 Redis Key，适用于风控等单 Key 命名空间。
     */
    public static String key(String namespace, String resource, String identifier) {
        validateSegment(namespace, "namespace", true);
        validateSegment(resource, "resource", false);
        validateSegment(identifier, "identifier", false);
        return namespace + ":" + resource + ":" + identifier;
    }

    /**
     * 生成可供 Lua 脚本复用的用户作用域前缀。
     */
    public static String scopedPrefix(String namespace, RedisKeyScope scope) {
        validateSegment(namespace, "namespace", true);
        if (scope == null) {
            throw new IllegalArgumentException("scope 不能为空");
        }
        return namespace + ":" + scope.hashTag();
    }

    private static void validateSegment(String value, String name, boolean allowColon) {
        if (value == null || value.isEmpty() || !value.equals(value.trim())
                || value.indexOf('{') >= 0 || value.indexOf('}') >= 0
                || (!allowColon && value.indexOf(':') >= 0)
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(name + " 非法");
        }
    }
}
