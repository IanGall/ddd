package cn.iantech.redis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RedisKeyBuilderTest {

    @Test
    void sameScopeKeysShareHashTag() {
        RedisKeyScope scope = RedisKeyBuilder.scope(1001);
        String profile = RedisKeyBuilder.key("auth:session:v4", scope, "session", "s1");
        String refresh = RedisKeyBuilder.key("auth:session:v4", scope, "refresh", "r1");

        assertThat(profile).isEqualTo("auth:session:v4:{1001}:session:s1");
        assertThat(refresh).isEqualTo("auth:session:v4:{1001}:refresh:r1");
        assertThat(RedisKeyBuilder.scopedPrefix("auth:session:v4", scope))
                .isEqualTo("auth:session:v4:{1001}");
        assertThat(RedisKeyBuilder.hashTag(scope)).isEqualTo("{1001}");
    }

    @Test
    void differentUserIdsDoNotCollide() {
        assertThat(RedisKeyBuilder.hashTag(RedisKeyBuilder.scope(1001)))
                .isNotEqualTo(RedisKeyBuilder.hashTag(RedisKeyBuilder.scope(1002)));
    }

    @Test
    void invalidScopeIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> RedisKeyBuilder.scope(0));
        assertThatIllegalArgumentException().isThrownBy(() -> RedisKeyBuilder.scope(-1));
    }

    @Test
    void invalidKeySegmentsAreRejected() {
        RedisKeyScope scope = RedisKeyBuilder.scope(3);
        assertThatIllegalArgumentException().isThrownBy(() -> RedisKeyBuilder.key("", scope, "r", "i"));
        assertThatIllegalArgumentException().isThrownBy(() -> RedisKeyBuilder.key("ns", scope, "r r", "i"));
        assertThatIllegalArgumentException().isThrownBy(() -> RedisKeyBuilder.key("ns", scope, "r", "i}"));
        assertThatIllegalArgumentException().isThrownBy(() -> RedisKeyBuilder.key("ns", null, "r", "i"));
    }

    @Test
    void unscopedKeysUseTheSameSegmentValidation() {
        assertThat(RedisKeyBuilder.key("auth:risk:v1", "ip", "hash"))
                .isEqualTo("auth:risk:v1:ip:hash");
        assertThatIllegalArgumentException().isThrownBy(() -> RedisKeyBuilder.key("ns", "r", "i i"));
        assertThatIllegalArgumentException().isThrownBy(() -> RedisKeyBuilder.key("ns", "r", "i:i"));
    }
}
