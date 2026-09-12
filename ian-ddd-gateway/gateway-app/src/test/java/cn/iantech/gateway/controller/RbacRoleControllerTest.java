package cn.iantech.gateway.controller;

import cn.iantech.api.model.rbac.CreateRbacRoleReq;
import cn.iantech.api.model.rbac.DeleteRbacRoleReq;
import cn.iantech.api.model.rbac.RbacRoleDTO;
import cn.iantech.api.model.rbac.UpdateRbacRoleReq;
import cn.iantech.common.model.Response;
import cn.iantech.gateway.model.RbacWebRequests;
import cn.iantech.gateway.service.GatewayRbacClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RbacRoleControllerTest {

    private final GatewayRbacClient client = mock(GatewayRbacClient.class);
    private final RbacRoleController controller = new RbacRoleController(client);

    @Test
    void shouldForwardCreateRoleFieldsToProvider() {
        RbacRoleDTO created = RbacRoleDTO.builder().id(21L).roleCode("ops").build();
        when(client.createRole(any())).thenReturn(created);

        Response<RbacRoleDTO> response = controller.createRole(
                new RbacWebRequests.CreateRole("ops", "运维", "运维角色", true));

        ArgumentCaptor<CreateRbacRoleReq> captor = ArgumentCaptor.forClass(CreateRbacRoleReq.class);
        verify(client).createRole(captor.capture());
        assertEquals("ops", captor.getValue().getRoleCode());
        assertEquals("运维", captor.getValue().getRoleName());
        assertEquals("运维角色", captor.getValue().getRoleDesc());
        assertEquals(Boolean.TRUE, captor.getValue().getStatus());
        assertEquals(created, response.getData());
    }

    @Test
    void shouldBindPathIdWhenUpdatingRole() {
        when(client.updateRole(any())).thenReturn(RbacRoleDTO.builder().id(21L).build());

        controller.updateRole(21L, new RbacWebRequests.UpdateRole("ops", "运维", null, false));

        ArgumentCaptor<UpdateRbacRoleReq> captor = ArgumentCaptor.forClass(UpdateRbacRoleReq.class);
        verify(client).updateRole(captor.capture());
        assertEquals(21L, captor.getValue().getId());
        assertEquals("ops", captor.getValue().getRoleCode());
        assertEquals(Boolean.FALSE, captor.getValue().getStatus());
    }

    @Test
    void shouldForwardDeleteRoleByPathId() {
        when(client.deleteRole(any())).thenReturn(true);

        Response<Boolean> response = controller.deleteRole(21L);

        ArgumentCaptor<DeleteRbacRoleReq> captor = ArgumentCaptor.forClass(DeleteRbacRoleReq.class);
        verify(client).deleteRole(captor.capture());
        assertEquals(21L, captor.getValue().getId());
        assertEquals(Boolean.TRUE, response.getData());
    }
}
