package cn.iantech.infrastructure.auth;

import cn.iantech.domain.auth.infra.IAuthSessionStore;
import cn.iantech.domain.auth.model.AuthSession;
import cn.iantech.domain.auth.model.AuthTokenReference;
import cn.iantech.redis.IRedisService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 Redis Lua 的 Auth 会话存储。同一用户范围内的所有会话键共享 Cluster Slot。
 */
@Service
public class RedisAuthSessionStore implements IAuthSessionStore {

    private static final String LUA_FUNCTIONS = """
            local function extendIndex(key, expiresAt, member)
                local existed = redis.call('EXISTS', key)
                redis.call('ZADD', key, expiresAt, member)
                if existed == 0 then
                    redis.call('PEXPIREAT', key, expiresAt)
                    return
                end
                local ttl = redis.call('PTTL', key)
                local desired = expiresAt - tonumber(redis.call('TIME')[1]) * 1000
                if ttl >= 0 and ttl < desired then
                    redis.call('PEXPIREAT', key, expiresAt)
                end
            end
            
            local function writeSession(sessionKey, accessKey, refreshKey, familyKey, userKey)
                redis.call('HSET', sessionKey,
                    'sessionId', ARGV[1], 'familyId', ARGV[2], 'accountId', ARGV[3],
                    'userId', ARGV[4], 'username', ARGV[5], 'userType', ARGV[6],
                    'accessTokenHash', ARGV[7], 'refreshTokenHash', ARGV[8],
                    'clientType', ARGV[9], 'deviceId', ARGV[10], 'ipAddress', ARGV[11],
                    'userAgent', ARGV[12], 'createdAt', ARGV[13],
                    'accessExpiresAt', ARGV[14], 'refreshExpiresAt', ARGV[15],
                    'revokedAt', ARGV[16], 'replacedBy', ARGV[17])
                redis.call('PEXPIREAT', sessionKey, ARGV[15])
                redis.call('SET', accessKey, ARGV[1], 'PXAT', ARGV[14])
                redis.call('SET', refreshKey, ARGV[1], 'PXAT', ARGV[15])
                extendIndex(familyKey, tonumber(ARGV[15]), ARGV[1])
                extendIndex(userKey, tonumber(ARGV[15]), ARGV[1])
            end
            
            local function revokeFamily(prefix, familyId, revokedAt)
                local familyKey = prefix .. 'family:' .. familyId
                local sessionIds = redis.call('ZRANGE', familyKey, 0, -1)
                for _, sessionId in ipairs(sessionIds) do
                    local sessionKey = prefix .. 'session:' .. sessionId
                    local revoked = redis.call('HGET', sessionKey, 'revokedAt')
                    if revoked and revoked == '' then
                        local accessHash = redis.call('HGET', sessionKey, 'accessTokenHash')
                        redis.call('HSET', sessionKey, 'revokedAt', revokedAt)
                        if accessHash and accessHash ~= '' then
                            redis.call('DEL', prefix .. 'access:' .. accessHash)
                        end
                    end
                end
            end
            """;

    private static final String CREATE_SCRIPT = LUA_FUNCTIONS + """
            if redis.call('EXISTS', KEYS[1]) == 1
                    or redis.call('EXISTS', KEYS[2]) == 1
                    or redis.call('EXISTS', KEYS[3]) == 1 then
                return 0
            end
            
            writeSession(KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5])
            local now = tonumber(ARGV[18])
            local maximum = tonumber(ARGV[19])
            local prefix = ARGV[20]
            redis.call('ZREMRANGEBYSCORE', KEYS[5], '-inf', now)
            
            local familyMap = {}
            local sessionIds = redis.call('ZRANGE', KEYS[5], 0, -1)
            for _, sessionId in ipairs(sessionIds) do
                local sessionKey = prefix .. 'session:' .. sessionId
                local values = redis.call('HMGET', sessionKey, 'familyId', 'createdAt', 'refreshExpiresAt', 'revokedAt')
                if values[1] and values[4] == '' and tonumber(values[3]) > now then
                    local current = familyMap[values[1]]
                    local createdAt = tonumber(values[2])
                    if not current or createdAt < current then
                        familyMap[values[1]] = createdAt
                    end
                end
            end
            
            local families = {}
            for familyId, createdAt in pairs(familyMap) do
                table.insert(families, { familyId = familyId, createdAt = createdAt })
            end
            table.sort(families, function(left, right)
                if left.createdAt == right.createdAt then
                    return left.familyId < right.familyId
                end
                return left.createdAt < right.createdAt
            end)
            for index = 1, math.max(0, #families - maximum) do
                revokeFamily(prefix, families[index].familyId, ARGV[18])
            end
            return 1
            """;

    private static final String ROTATE_SCRIPT = LUA_FUNCTIONS + """
            local oldSessionId = redis.call('GET', KEYS[1])
            if not oldSessionId then
                return 0
            end
            local prefix = ARGV[19]
            local oldSessionKey = prefix .. 'session:' .. oldSessionId
            local old = redis.call('HMGET', oldSessionKey, 'familyId', 'accountId', 'userId', 'userType',
                'createdAt', 'refreshExpiresAt', 'revokedAt', 'replacedBy', 'accessTokenHash')
            if not old[1] or tonumber(old[6]) <= tonumber(ARGV[18]) then
                return 0
            end
            if old[7] ~= '' or old[8] ~= '' then
                revokeFamily(prefix, old[1], ARGV[18])
                return 2
            end
            if old[1] ~= ARGV[2] or old[2] ~= ARGV[3] or old[3] ~= ARGV[4]
                    or old[4] ~= ARGV[6] or old[5] ~= ARGV[13] then
                return 0
            end
            if redis.call('EXISTS', KEYS[2]) == 1
                    or redis.call('EXISTS', KEYS[3]) == 1
                    or redis.call('EXISTS', KEYS[4]) == 1 then
                return 0
            end
            
            redis.call('HSET', oldSessionKey, 'revokedAt', ARGV[18], 'replacedBy', ARGV[1])
            if old[9] and old[9] ~= '' then
                redis.call('DEL', prefix .. 'access:' .. old[9])
            end
            writeSession(KEYS[2], KEYS[3], KEYS[4], KEYS[5], KEYS[6])
            return 1
            """;

    private static final String REVOKE_SESSION_SCRIPT = LUA_FUNCTIONS + """
            local familyId = redis.call('HGET', KEYS[1], 'familyId')
            if familyId then
                revokeFamily(ARGV[1], familyId, ARGV[2])
            end
            return 1
            """;

    private static final String REVOKE_FAMILY_SCRIPT = LUA_FUNCTIONS + """
            revokeFamily(ARGV[1], ARGV[2], ARGV[3])
            return 1
            """;

    private final IRedisService redisService;

    public RedisAuthSessionStore(IRedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public void create(AuthSession session, int maximumActiveFamilies) {
        Long userId = session.userId();
        List<String> keys = List.of(AuthRedisKey.session(userId, session.sessionId()),
                AuthRedisKey.access(userId, session.accessTokenHash()),
                AuthRedisKey.refresh(userId, session.refreshTokenHash()),
                AuthRedisKey.family(userId, session.familyId()), AuthRedisKey.user(userId));
        List<Object> arguments = sessionArguments(session);
        arguments.add(Instant.now().toEpochMilli());
        arguments.add(maximumActiveFamilies);
        arguments.add(AuthRedisKey.scopePrefix(userId));
        Long result = redisService.executeLongScript(CREATE_SCRIPT, keys, arguments);
        if (result == null || result != 1L) {
            throw new IllegalStateException("Auth 会话令牌摘要发生冲突");
        }
    }

    @Override
    public RotationResult rotate(AuthTokenReference refreshToken, AuthSession replacement, Instant rotatedAt) {
        Long userId = refreshToken.userId();
        if (!userId.equals(replacement.userId())) {
            throw new IllegalArgumentException("轮换前后的会话范围必须一致");
        }
        List<String> keys = List.of(AuthRedisKey.refresh(userId, refreshToken.tokenHash()),
                AuthRedisKey.session(userId, replacement.sessionId()),
                AuthRedisKey.access(userId, replacement.accessTokenHash()),
                AuthRedisKey.refresh(userId, replacement.refreshTokenHash()),
                AuthRedisKey.family(userId, replacement.familyId()), AuthRedisKey.user(userId));
        List<Object> arguments = sessionArguments(replacement);
        arguments.add(rotatedAt.toEpochMilli());
        arguments.add(AuthRedisKey.scopePrefix(userId));
        Long result = redisService.executeLongScript(ROTATE_SCRIPT, keys, arguments);
        if (result == null || result == 0L) {
            return RotationResult.INVALID;
        }
        return result == 2L ? RotationResult.REPLAY_REVOKED : RotationResult.ROTATED;
    }

    @Override
    public void revokeSession(Long userId, String sessionId, Instant revokedAt) {
        redisService.executeLongScript(REVOKE_SESSION_SCRIPT, List.of(AuthRedisKey.session(userId, sessionId)),
                List.of(AuthRedisKey.scopePrefix(userId), revokedAt.toEpochMilli()));
    }

    @Override
    public void revokeFamily(Long userId, String familyId, Instant revokedAt) {
        redisService.executeLongScript(REVOKE_FAMILY_SCRIPT, List.of(AuthRedisKey.family(userId, familyId)),
                List.of(AuthRedisKey.scopePrefix(userId), familyId, revokedAt.toEpochMilli()));
    }

    @Override
    public Optional<AuthSession> findByAccessToken(AuthTokenReference token) {
        String sessionId = redisService.getString(AuthRedisKey.access(token.userId(), token.tokenHash()));
        return sessionId == null ? Optional.empty() : findBySessionId(token.userId(), sessionId);
    }

    @Override
    public Optional<AuthSession> findByRefreshToken(AuthTokenReference token) {
        String sessionId = redisService.getString(AuthRedisKey.refresh(token.userId(), token.tokenHash()));
        return sessionId == null ? Optional.empty() : findBySessionId(token.userId(), sessionId);
    }

    @Override
    public Optional<AuthSession> findBySessionId(Long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> sessionValues = redisService.readStringHash(AuthRedisKey.session(userId, sessionId));
        return sessionValues.isEmpty() ? Optional.empty() : Optional.of(fromMap(sessionValues))
                .filter(session -> userId.equals(session.userId()));
    }

    @Override
    public List<AuthSession> findActiveByUser(Long userId) {
        Instant now = Instant.now();
        String indexKey = AuthRedisKey.user(userId);
        redisService.removeSortedSetByScore(indexKey, Double.NEGATIVE_INFINITY, true, now.toEpochMilli(), true);
        List<String> sessionIds = redisService.rangeSortedSetByScore(indexKey, now.toEpochMilli(), false,
                Double.POSITIVE_INFINITY, true);
        return redisService.readStringHashes(sessionIds.stream()
                        .map(sessionId -> AuthRedisKey.session(userId, sessionId))
                        .toList()).values().stream()
                .filter(values -> !values.isEmpty())
                .map(this::fromMap)
                .filter(session -> userId.equals(session.userId()))
                .filter(session -> session.refreshActive(now))
                .toList();
    }

    private List<Object> sessionArguments(AuthSession session) {
        List<Object> values = new ArrayList<>(20);
        values.add(session.sessionId());
        values.add(session.familyId());
        values.add(nullable(session.accountId()));
        values.add(session.userId().toString());
        values.add(session.username());
        values.add(session.userType());
        values.add(session.accessTokenHash());
        values.add(session.refreshTokenHash());
        values.add(session.clientType());
        values.add(session.deviceId());
        values.add(session.ipAddress());
        values.add(session.userAgent());
        values.add(session.createdAt().toEpochMilli());
        values.add(session.accessExpiresAt().toEpochMilli());
        values.add(session.refreshExpiresAt().toEpochMilli());
        values.add(nullable(session.revokedAt()));
        values.add(nullable(session.replacedBy()));
        return values;
    }

    private AuthSession fromMap(Map<String, String> values) {
        return new AuthSession(values.get("sessionId"), values.get("familyId"), nullableLong(values.get("accountId")),
                nullableLong(values.get("userId")), values.get("username"), values.get("userType"),
                values.get("accessTokenHash"), values.get("refreshTokenHash"), values.get("clientType"),
                values.get("deviceId"), values.get("ipAddress"), values.get("userAgent"),
                instant(values.get("createdAt")), instant(values.get("accessExpiresAt")),
                instant(values.get("refreshExpiresAt")), nullableInstant(values.get("revokedAt")),
                emptyToNull(values.get("replacedBy")));
    }

    private Object nullable(Object value) {
        return value == null ? "" : value instanceof Instant instant ? instant.toEpochMilli() : value.toString();
    }

    private Instant instant(String value) {
        return Instant.ofEpochMilli(Long.parseLong(value));
    }

    private Long nullableLong(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private Instant nullableInstant(String value) {
        String nonEmpty = emptyToNull(value);
        return nonEmpty == null ? null : instant(nonEmpty);
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

}
