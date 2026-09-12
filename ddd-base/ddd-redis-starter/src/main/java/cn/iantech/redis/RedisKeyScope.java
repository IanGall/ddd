package cn.iantech.redis;

/**
 * Redis Cluster 用户级 Key 作用域。
 *
 * <p>作用域直接使用全局唯一用户 ID 组成 Hash Tag，确保同一用户的多个 Key
 * 被 Redis Cluster 路由到同一个 hash slot。</p>
 *
 * @param userId 用户 ID，必须为全局唯一的正数
 */
public record RedisKeyScope(long userId) {

    public RedisKeyScope {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId 必须为正数");
        }
    }

    /**
     * 返回 Redis Cluster Hash Tag，格式为 {@code {userId}}。
     *
     * @return 用户 Hash Tag
     */
    public String hashTag() {
        return "{" + userId + "}";
    }
}
