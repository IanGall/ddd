package cn.iantech.trigger.rpc;

import cn.iantech.api.model.rbac.CreateRbacUserReq;
import cn.iantech.api.model.rbac.DeleteRbacUserReq;
import cn.iantech.api.model.rbac.QueryRbacUserPageReq;
import cn.iantech.api.model.rbac.QueryUserRoleIdsReq;
import cn.iantech.api.model.rbac.QueryUserRoleIdsResp;
import cn.iantech.api.model.rbac.RbacUserDTO;
import cn.iantech.api.model.rbac.RbacUserPageDTO;
import cn.iantech.api.model.rbac.UpdateRbacUserReq;
import cn.iantech.cases.model.Actor;
import cn.iantech.cases.rbac.model.RbacCaseCommands;
import cn.iantech.cases.rbac.service.RbacCaseService;
import cn.iantech.domain.model.DomainPage;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import cn.iantech.trigger.context.ActorResolver;
import cn.iantech.trigger.convertor.RbacCommandConvertor;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RBAC 管理端 Dubbo 入站适配器：可信 Actor 解析、命令映射、分页装配与空请求容错。
 *
 * <p>用例层的越权校验完全依赖入站解析出的 Actor，因此所有写操作都断言 Actor 原样透传。</p>
 */
class RbacServiceTest {

    private static final Actor ACTOR = new Actor("req-1", 1001L, 2002L, "alice", "ADMIN", "gateway");
    private static final RbacUserEntity USER = RbacUserEntity.builder()
            .id(3003L).accountId(1001L).username("bob").status(Boolean.TRUE).build();

    private final RbacCaseService rbacCaseService = mock(RbacCaseService.class);
    private final ActorResolver actorResolver = mock(ActorResolver.class);
    private final Converter converter = mock(Converter.class);
    private final RbacCommandConvertor commandConvertor = Mappers.getMapper(RbacCommandConvertor.class);
    private final RbacService service =
            new RbacService(rbacCaseService, actorResolver, converter, commandConvertor);

    @Test
    void shouldResolveActorMapCommandAndConvertOnCreateUser() {
        RbacUserDTO converted = new RbacUserDTO();
        when(actorResolver.resolve()).thenReturn(ACTOR);
        when(rbacCaseService.createUser(any(), any())).thenReturn(USER);
        when(converter.convert(any(RbacUserEntity.class), eq(RbacUserDTO.class))).thenReturn(converted);

        RbacUserDTO dto = service.createUser(CreateRbacUserReq.builder()
                .username("bob").password("pwd-1234").displayName("Bob")
                .email("bob@example.com").mobile("13900000000").status(Boolean.TRUE).build());

        assertSame(converted, dto);

        ArgumentCaptor<RbacCaseCommands.CreateUser> captor =
                ArgumentCaptor.forClass(RbacCaseCommands.CreateUser.class);
        verify(rbacCaseService).createUser(eq(ACTOR), captor.capture());
        assertEquals("bob", captor.getValue().username());
        assertEquals("pwd-1234", captor.getValue().password());
        assertEquals("13900000000", captor.getValue().mobile());
        verify(converter).convert(USER, RbacUserDTO.class);
    }

    @Test
    void shouldKeepPageNumbersAndConvertListOnQueryUserPage() {
        RbacUserDTO converted = new RbacUserDTO();
        when(actorResolver.resolve()).thenReturn(ACTOR);
        when(rbacCaseService.queryUserPage(any(), any()))
                .thenReturn(new DomainPage<>(1L, 2, 20, List.of(USER)));
        when(converter.convert(ArgumentMatchers.<RbacUserEntity>anyList(), eq(RbacUserDTO.class)))
                .thenReturn(List.of(converted));

        RbacUserPageDTO dto = service.queryUserPage(
                QueryRbacUserPageReq.builder().pageNum(2).pageSize(20).username("bob").build());

        assertEquals(1L, dto.getTotal());
        assertEquals(2, dto.getPageNum());
        assertEquals(20, dto.getPageSize());
        assertEquals(List.of(converted), dto.getList());

        ArgumentCaptor<RbacCaseCommands.QueryUserPage> captor =
                ArgumentCaptor.forClass(RbacCaseCommands.QueryUserPage.class);
        verify(rbacCaseService).queryUserPage(eq(ACTOR), captor.capture());
        assertEquals(2, captor.getValue().pageNum());
        assertEquals("bob", captor.getValue().username());
    }

    @Test
    void shouldPassNullPasswordThroughOnUpdateUser() {
        when(actorResolver.resolve()).thenReturn(ACTOR);
        when(rbacCaseService.updateUser(any(), any())).thenReturn(USER);
        when(converter.convert(any(RbacUserEntity.class), eq(RbacUserDTO.class))).thenReturn(new RbacUserDTO());

        service.updateUser(UpdateRbacUserReq.builder().id(3003L).displayName("Bob").build());

        ArgumentCaptor<RbacCaseCommands.UpdateUser> captor =
                ArgumentCaptor.forClass(RbacCaseCommands.UpdateUser.class);
        verify(rbacCaseService).updateUser(eq(ACTOR), captor.capture());
        assertEquals(3003L, captor.getValue().id());
        assertEquals(null, captor.getValue().password());
    }

    @Test
    void shouldTolerateNullRequestOnDeleteUser() {
        when(actorResolver.resolve()).thenReturn(ACTOR);
        when(rbacCaseService.deleteUser(any(), any())).thenReturn(Boolean.FALSE);

        assertFalse(service.deleteUser(null));

        verify(rbacCaseService).deleteUser(ACTOR, null);
    }

    @Test
    void shouldReturnNullUserIdWhenQueryUserRoleIdsRequestIsNull() {
        when(actorResolver.resolve()).thenReturn(ACTOR);
        when(rbacCaseService.queryUserRoleIds(any(), any())).thenReturn(List.of(1L, 2L));

        QueryUserRoleIdsResp resp = service.queryUserRoleIds(null);

        assertEquals(null, resp.getUserId());
        assertEquals(List.of(1L, 2L), resp.getRoleIds());
        verify(rbacCaseService).queryUserRoleIds(ACTOR, null);

        QueryUserRoleIdsResp withId = service.queryUserRoleIds(
                QueryUserRoleIdsReq.builder().userId(3003L).build());
        assertEquals(3003L, withId.getUserId());
    }
}
