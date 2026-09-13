package cn.iantech.trigger.convertor;

import cn.iantech.api.model.auth.AuthLoginReq;
import cn.iantech.api.model.auth.AuthRefreshReq;
import cn.iantech.api.model.channel.ChannelSignatureVerifyReq;
import cn.iantech.api.model.channel.CreateChannelCredentialReq;
import cn.iantech.api.model.channel.UpdateChannelCredentialStatusReq;
import cn.iantech.api.model.customer.CustomerLoginReq;
import cn.iantech.api.model.rbac.CreateRbacUserReq;
import cn.iantech.api.model.rbac.ReplaceUserRolesReq;
import cn.iantech.cases.auth.model.AuthCaseModels.LoginCommand;
import cn.iantech.cases.auth.model.AuthCaseModels.RefreshCommand;
import cn.iantech.cases.channel.model.ChannelCaseModels.CreateCredential;
import cn.iantech.cases.channel.model.ChannelCaseModels.SignatureCommand;
import cn.iantech.cases.channel.model.ChannelCaseModels.UpdateCredentialStatus;
import cn.iantech.cases.rbac.model.RbacCaseCommands;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * RPC 请求 DTO 到用例命令的字段映射契约。
 *
 * <p>网关只依赖 {@code *-api} DTO，标准服务靠 MapStruct 生成的实现完成字段搬运；
 * 该契约若与 DTO 漂移，编译期 {@code unmappedTargetPolicy=ERROR} 会失败，字段语义错误则由本测试兜底。</p>
 */
class CommandConvertorContractTest {

    private final AuthCommandConvertor authConvertor = Mappers.getMapper(AuthCommandConvertor.class);
    private final RbacCommandConvertor rbacConvertor = Mappers.getMapper(RbacCommandConvertor.class);
    private final ChannelCredentialCommandConvertor channelConvertor =
            Mappers.getMapper(ChannelCredentialCommandConvertor.class);

    @Test
    void shouldMapAdminLoginRequest() {
        AuthLoginReq req = AuthLoginReq.builder()
                .loginName("alice@1001.com").password("pwd").clientType("WEB")
                .deviceId("device-1").ipAddress("127.0.0.1").userAgent("junit").build();

        LoginCommand command = authConvertor.toCommand(req);

        assertEquals("alice@1001.com", command.loginName());
        assertEquals("pwd", command.password());
        assertEquals("WEB", command.clientType());
        assertEquals("device-1", command.deviceId());
        assertEquals("127.0.0.1", command.ipAddress());
        assertEquals("junit", command.userAgent());
    }

    @Test
    void shouldMapCustomerLoginRequest() {
        CustomerLoginReq req = new CustomerLoginReq();
        req.setLoginName("13800000000");
        req.setPassword("pwd");
        req.setClientType("APP");
        req.setDeviceId("ios-1");
        req.setIpAddress("10.0.0.1");
        req.setUserAgent("app/1.0");

        LoginCommand command = authConvertor.toCommand(req);

        assertEquals("13800000000", command.loginName());
        assertEquals("APP", command.clientType());
        assertEquals("ios-1", command.deviceId());
        assertEquals("10.0.0.1", command.ipAddress());
        assertEquals("app/1.0", command.userAgent());
    }

    @Test
    void shouldMapRefreshRequest() {
        AuthRefreshReq req = AuthRefreshReq.builder()
                .refreshToken("refresh-1").expectedSubjectType("ADMIN")
                .clientType("WEB").ipAddress("127.0.0.1").userAgent("junit").build();

        RefreshCommand command = authConvertor.toCommand(req);

        assertEquals("refresh-1", command.refreshToken());
        assertEquals("ADMIN", command.expectedSubjectType());
        assertEquals("127.0.0.1", command.ipAddress());
        assertEquals("junit", command.userAgent());
    }

    @Test
    void shouldMapChannelSignatureRequest() {
        ChannelSignatureVerifyReq req = ChannelSignatureVerifyReq.builder()
                .channelCode("CH-1").secretVersion(3L).timestamp(1726000000L)
                .signature("sig").canonicalRequest("GET\n/api/external").build();

        SignatureCommand command = authConvertor.toCommand(req);

        assertEquals("CH-1", command.channelCode());
        assertEquals(3L, command.secretVersion());
        assertEquals(1726000000L, command.timestamp());
        assertEquals("sig", command.signature());
        assertEquals("GET\n/api/external", command.canonicalRequest());
    }

    @Test
    void shouldMapRbacCreateUserRequest() {
        CreateRbacUserReq req = CreateRbacUserReq.builder()
                .username("bob").password("pwd").displayName("Bob")
                .email("bob@example.com").mobile("13900000000").status(Boolean.TRUE).build();

        RbacCaseCommands.CreateUser command = rbacConvertor.toCommand(req);

        assertEquals("bob", command.username());
        assertEquals("pwd", command.password());
        assertEquals("Bob", command.displayName());
        assertEquals("bob@example.com", command.email());
        assertEquals("13900000000", command.mobile());
        assertEquals(Boolean.TRUE, command.status());
    }

    @Test
    void shouldMapReplaceUserRolesRequest() {
        ReplaceUserRolesReq req = ReplaceUserRolesReq.builder().userId(99L)
                .roleIds(List.of(1L, 2L, 3L)).build();

        RbacCaseCommands.ReplaceUserRoles command = rbacConvertor.toCommand(req);

        assertEquals(99L, command.userId());
        assertEquals(List.of(1L, 2L, 3L), command.roleIds());
    }

    @Test
    void shouldMapChannelCredentialRequests() {
        CreateCredential created = channelConvertor.toCommand(
                CreateChannelCredentialReq.builder().channelName("示例渠道").build());
        assertEquals("示例渠道", created.channelName());

        UpdateCredentialStatus status = channelConvertor.toCommand(
                UpdateChannelCredentialStatusReq.builder().id(7L).status(Boolean.FALSE).build());
        assertEquals(7L, status.id());
        assertEquals(Boolean.FALSE, status.status());
    }

    @Test
    void shouldPassNullSourceThroughAsNull() {
        assertNull(authConvertor.toCommand((AuthLoginReq) null));
        assertNull(authConvertor.toCommand((CustomerLoginReq) null));
        assertNull(authConvertor.toCommand((AuthRefreshReq) null));
        assertNull(authConvertor.toCommand((ChannelSignatureVerifyReq) null));
        assertNull(rbacConvertor.toCommand((CreateRbacUserReq) null));
        assertNull(channelConvertor.toCommand((CreateChannelCredentialReq) null));
    }
}
