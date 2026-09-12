package cn.iantech.gateway.controller;

import cn.iantech.api.model.rbac.CreateRbacPermissionReq;
import cn.iantech.api.model.rbac.DeleteRbacPermissionReq;
import cn.iantech.api.model.rbac.QueryRbacPermissionPageReq;
import cn.iantech.api.model.rbac.RbacPermissionDTO;
import cn.iantech.api.model.rbac.RbacPermissionPageDTO;
import cn.iantech.api.model.rbac.UpdateRbacPermissionReq;
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

class RbacPermissionControllerTest {

    private final GatewayRbacClient client = mock(GatewayRbacClient.class);
    private final RbacPermissionController controller = new RbacPermissionController(client);

    @Test
    void shouldForwardCreatePermissionFieldsToProvider() {
        RbacPermissionDTO created = RbacPermissionDTO.builder().id(31L).permCode("ops:read").build();
        when(client.createPermission(any())).thenReturn(created);

        Response<RbacPermissionDTO> response = controller.createPermission(new RbacWebRequests.CreatePermission(
                "ops:read", "只读", 1, 0L, "/api/ops", "GET", true));

        ArgumentCaptor<CreateRbacPermissionReq> captor = ArgumentCaptor.forClass(CreateRbacPermissionReq.class);
        verify(client).createPermission(captor.capture());
        CreateRbacPermissionReq request = captor.getValue();
        assertEquals("ops:read", request.getPermCode());
        assertEquals("只读", request.getPermName());
        assertEquals(1, request.getPermType());
        assertEquals(0L, request.getParentId());
        assertEquals("/api/ops", request.getPath());
        assertEquals("GET", request.getMethod());
        assertEquals(created, response.getData());
    }

    @Test
    void shouldBindPathIdWhenUpdatingPermission() {
        when(client.updatePermission(any())).thenReturn(RbacPermissionDTO.builder().id(31L).build());

        controller.updatePermission(31L, new RbacWebRequests.UpdatePermission("只读", 1, 0L, null, "GET", false));

        ArgumentCaptor<UpdateRbacPermissionReq> captor = ArgumentCaptor.forClass(UpdateRbacPermissionReq.class);
        verify(client).updatePermission(captor.capture());
        assertEquals(31L, captor.getValue().getId());
        assertEquals("只读", captor.getValue().getPermName());
        assertEquals(Boolean.FALSE, captor.getValue().getStatus());
    }

    @Test
    void shouldForwardPermissionPageQuery() {
        when(client.queryPermissionPage(any())).thenReturn(RbacPermissionPageDTO.builder().build());

        controller.queryPermissionPage(1, 20, "ops:read", null, 1, 0L, true);

        ArgumentCaptor<QueryRbacPermissionPageReq> captor = ArgumentCaptor.forClass(QueryRbacPermissionPageReq.class);
        verify(client).queryPermissionPage(captor.capture());
        QueryRbacPermissionPageReq request = captor.getValue();
        assertEquals(1, request.getPageNum());
        assertEquals(20, request.getPageSize());
        assertEquals("ops:read", request.getPermCode());
        assertEquals(1, request.getPermType());
        assertEquals(0L, request.getParentId());
        assertEquals(Boolean.TRUE, request.getStatus());
    }

    @Test
    void shouldForwardDeletePermissionByPathId() {
        when(client.deletePermission(any())).thenReturn(true);

        Response<Boolean> response = controller.deletePermission(31L);

        ArgumentCaptor<DeleteRbacPermissionReq> captor = ArgumentCaptor.forClass(DeleteRbacPermissionReq.class);
        verify(client).deletePermission(captor.capture());
        assertEquals(31L, captor.getValue().getId());
        assertEquals(Boolean.TRUE, response.getData());
    }
}
