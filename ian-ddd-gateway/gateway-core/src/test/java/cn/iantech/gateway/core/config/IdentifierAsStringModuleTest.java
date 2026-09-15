package cn.iantech.gateway.core.config;

import cn.iantech.api.model.auth.AuthTokenDTO;
import cn.iantech.api.model.channel.ChannelCredentialDTO;
import cn.iantech.api.model.rbac.QueryUserRoleIdsResp;
import cn.iantech.api.model.rbac.RbacUserDTO;
import cn.iantech.common.model.PageResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 标识字段按字符串出网的契约测试。
 *
 * <p>这些 ID 的量级（约 8.7e17）超过 JavaScript 的 {@code Number.MAX_SAFE_INTEGER}（2^53），
 * 一旦以 JSON number 出网就会被浏览器 {@code JSON.parse} 改写低位。</p>
 */
class IdentifierAsStringModuleTest {

    /** 真实量级的雪花 ID：它以 number 出网时，浏览器读到的值会与它不同。 */
    private static final long SNOWFLAKE_ID = 869643386981388293L;

    /** 与 {@link #SNOWFLAKE_ID} 相邻、但在 JavaScript 里无法区分的整数。 */
    private static final long NEIGHBOURING_ID = 869643386981388299L;

    private final ObjectMapper gatewayMapper = JsonMapper.builder()
            .addModule(new IdentifierAsStringModule())
            .build();

    private final ObjectMapper plainMapper = JsonMapper.builder().build();

    @Test
    void shouldShowThatJavaScriptCannotTellAdjacentSnowflakeIdsApart() {
        // 本模块存在的理由：同一量级的相邻整数在 JS 里会塌缩成同一个 double，
        // 浏览器读到的 ID 不再是后端发出的那个值。
        assertThat((double) NEIGHBOURING_ID).isEqualTo((double) SNOWFLAKE_ID);
        assertThat((long) (double) NEIGHBOURING_ID).isNotEqualTo(NEIGHBOURING_ID);
    }

    @Test
    void shouldSerializeScalarIdentifiersAsStrings() {
        String json = gatewayMapper.writeValueAsString(
                RbacUserDTO.builder().id(SNOWFLAKE_ID).accountId(1L).username("u1").build());

        assertThat(json).contains("\"id\":\"" + SNOWFLAKE_ID + "\"");
        assertThat(json).contains("\"accountId\":\"1\"");
        assertThat(json).contains("\"username\":\"u1\"");
    }

    @Test
    void shouldSerializeIdentifierCollectionsAsStringArrays() {
        String json = gatewayMapper.writeValueAsString(
                QueryUserRoleIdsResp.builder().userId(SNOWFLAKE_ID).roleIds(List.of(1L, 2L)).build());

        assertThat(json).contains("\"userId\":\"" + SNOWFLAKE_ID + "\"");
        assertThat(json).contains("\"roleIds\":[\"1\",\"2\"]");
        // 集合必须逐元素成串，不能整个列表 toString 成一个字符串
        assertThat(json).doesNotContain("\"[1, 2]\"");
    }

    @Test
    void shouldKeepNonIdentifierNumbersAsNumbers() {
        // 版本号、时长、分页总数都不是标识，必须保持数字，否则前端要做无谓的解析
        assertThat(gatewayMapper.writeValueAsString(AuthTokenDTO.builder()
                .accessToken("a").refreshToken("r").expiresIn(1800L).refreshExpiresIn(2592000L).build()))
                .contains("\"expiresIn\":1800")
                .contains("\"refreshExpiresIn\":2592000");

        assertThat(gatewayMapper.writeValueAsString(
                ChannelCredentialDTO.builder().id(SNOWFLAKE_ID).secretVersion(3L).build()))
                .contains("\"id\":\"" + SNOWFLAKE_ID + "\"")
                .contains("\"secretVersion\":3");

        assertThat(gatewayMapper.writeValueAsString(PageResponse.<String>builder()
                .total(SNOWFLAKE_ID).pageNum(1).pageSize(10).list(List.of("x")).build()))
                .contains("\"total\":" + SNOWFLAKE_ID)
                .contains("\"pageNum\":1");
    }

    @Test
    void shouldPreserveNullIdentifiers() {
        String json = gatewayMapper.writeValueAsString(
                RbacUserDTO.builder().id(null).accountId(null).username("u1").build());

        assertThat(json).contains("\"id\":null").contains("\"accountId\":null");
    }

    @Test
    void shouldStillDeserializeStringIdentifiersBackIntoLongFields() {
        // 只改序列化方向：前端把字符串原样回传时，入参仍要能落到 Long 字段上
        RbacUserDTO user = gatewayMapper.readValue(
                "{\"id\":\"" + SNOWFLAKE_ID + "\",\"accountId\":\"1\",\"username\":\"u1\"}",
                RbacUserDTO.class);

        assertThat(user.getId()).isEqualTo(SNOWFLAKE_ID);
        assertThat(user.getAccountId()).isEqualTo(1L);

        QueryUserRoleIdsResp roles = gatewayMapper.readValue(
                "{\"userId\":\"" + SNOWFLAKE_ID + "\",\"roleIds\":[\"1\",\"2\"]}",
                QueryUserRoleIdsResp.class);

        assertThat(roles.getUserId()).isEqualTo(SNOWFLAKE_ID);
        assertThat(roles.getRoleIds()).containsExactly(1L, 2L);
    }

    @Test
    void shouldNotAffectMapperWithoutTheModule() {
        // 反向对照：不加模块时仍以数字出网——这正是被修的缺陷形态
        assertThat(plainMapper.writeValueAsString(RbacUserDTO.builder().id(SNOWFLAKE_ID).build()))
                .contains("\"id\":" + SNOWFLAKE_ID)
                .doesNotContain("\"id\":\"");
    }
}
