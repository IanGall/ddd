package cn.iantech.gateway.controller;

import cn.iantech.api.model.rbac.QueryRolePermissionIdsReq;
import cn.iantech.api.model.rbac.QueryRolePermissionIdsResp;
import cn.iantech.api.model.rbac.QueryUserRoleIdsReq;
import cn.iantech.api.model.rbac.QueryUserRoleIdsResp;
import cn.iantech.api.model.rbac.ReplaceRolePermissionsReq;
import cn.iantech.api.model.rbac.ReplaceUserRolesReq;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.model.RbacWebRequests;
import cn.iantech.gateway.service.GatewayRbacClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RbacRelationControllerTest {

    private final GatewayRbacClient client = mock(GatewayRbacClient.class);
    private final RbacRelationController controller = new RbacRelationController(client);

    @Test
    void shouldBindPathUserIdWhenReplacingUserRoles() {
        when(client.replaceUserRoles(any())).thenReturn(true);

        Response<Boolean> response = controller.replaceUserRoles(11L,
                new RbacWebRequests.UserRoles(List.of(21L, 22L)));

        ArgumentCaptor<ReplaceUserRolesReq> captor = ArgumentCaptor.forClass(ReplaceUserRolesReq.class);
        verify(client).replaceUserRoles(captor.capture());
        assertEquals(11L, captor.getValue().getUserId());
        assertEquals(List.of(21L, 22L), captor.getValue().getRoleIds());
        assertEquals(Boolean.TRUE, response.getData());
    }

    @Test
    void shouldForwardQueryUserRoleIds() {
        QueryUserRoleIdsResp expected = QueryUserRoleIdsResp.builder().userId(11L).roleIds(List.of(21L)).build();
        when(client.queryUserRoleIds(any())).thenReturn(expected);

        Response<QueryUserRoleIdsResp> response = controller.queryUserRoleIds(11L);

        ArgumentCaptor<QueryUserRoleIdsReq> captor = ArgumentCaptor.forClass(QueryUserRoleIdsReq.class);
        verify(client).queryUserRoleIds(captor.capture());
        assertEquals(11L, captor.getValue().getUserId());
        assertEquals(expected, response.getData());
    }

    @Test
    void shouldBindPathRoleIdWhenReplacingRolePermissions() {
        when(client.replaceRolePermissions(any())).thenReturn(true);

        controller.replaceRolePermissions(21L, new RbacWebRequests.RolePermissions(List.of(31L, 32L)));

        ArgumentCaptor<ReplaceRolePermissionsReq> captor = ArgumentCaptor.forClass(ReplaceRolePermissionsReq.class);
        verify(client).replaceRolePermissions(captor.capture());
        assertEquals(21L, captor.getValue().getRoleId());
        assertEquals(List.of(31L, 32L), captor.getValue().getPermissionIds());
    }

    @Test
    void shouldForwardQueryRolePermissionIds() {
        QueryRolePermissionIdsResp expected = QueryRolePermissionIdsResp.builder()
                .roleId(21L).permissionIds(List.of(31L)).build();
        when(client.queryRolePermissionIds(any())).thenReturn(expected);

        Response<QueryRolePermissionIdsResp> response = controller.queryRolePermissionIds(21L);

        ArgumentCaptor<QueryRolePermissionIdsReq> captor = ArgumentCaptor.forClass(QueryRolePermissionIdsReq.class);
        verify(client).queryRolePermissionIds(captor.capture());
        assertEquals(21L, captor.getValue().getRoleId());
        assertEquals(expected, response.getData());
    }
}
