package cn.iantech.gateway.controller;

import cn.iantech.api.model.rbac.CreateRbacUserReq;
import cn.iantech.api.model.rbac.DeleteRbacUserReq;
import cn.iantech.api.model.rbac.QueryRbacUserPageReq;
import cn.iantech.api.model.rbac.RbacUserDTO;
import cn.iantech.api.model.rbac.RbacUserPageDTO;
import cn.iantech.api.model.rbac.UpdateRbacUserReq;
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

class RbacUserControllerTest {

    private final GatewayRbacClient client = mock(GatewayRbacClient.class);
    private final RbacUserController controller = new RbacUserController(client);

    @Test
    void shouldForwardCreateUserFieldsToProvider() {
        RbacUserDTO created = RbacUserDTO.builder().id(11L).username("operator").build();
        when(client.createUser(any())).thenReturn(created);

        Response<RbacUserDTO> response = controller.createUser(new RbacWebRequests.CreateUser(
                "operator", "password-123", "运营", "ops@example.com", "13800000000", true));

        ArgumentCaptor<CreateRbacUserReq> captor = ArgumentCaptor.forClass(CreateRbacUserReq.class);
        verify(client).createUser(captor.capture());
        CreateRbacUserReq request = captor.getValue();
        assertEquals("operator", request.getUsername());
        assertEquals("password-123", request.getPassword());
        assertEquals("运营", request.getDisplayName());
        assertEquals("ops@example.com", request.getEmail());
        assertEquals("13800000000", request.getMobile());
        assertEquals(Boolean.TRUE, request.getStatus());
        assertEquals(created, response.getData());
    }

    @Test
    void shouldBindPathIdWhenUpdatingUser() {
        when(client.updateUser(any())).thenReturn(RbacUserDTO.builder().id(11L).build());

        controller.updateUser(11L, new RbacWebRequests.UpdateUser("password-456", "新名称", null, null, false));

        ArgumentCaptor<UpdateRbacUserReq> captor = ArgumentCaptor.forClass(UpdateRbacUserReq.class);
        verify(client).updateUser(captor.capture());
        assertEquals(11L, captor.getValue().getId());
        assertEquals("password-456", captor.getValue().getPassword());
        assertEquals(Boolean.FALSE, captor.getValue().getStatus());
    }

    @Test
    void shouldForwardUserPageQuery() {
        when(client.queryUserPage(any())).thenReturn(RbacUserPageDTO.builder().build());

        controller.queryUserPage(2, 50, "operator", true);

        ArgumentCaptor<QueryRbacUserPageReq> captor = ArgumentCaptor.forClass(QueryRbacUserPageReq.class);
        verify(client).queryUserPage(captor.capture());
        assertEquals(2, captor.getValue().getPageNum());
        assertEquals(50, captor.getValue().getPageSize());
        assertEquals("operator", captor.getValue().getUsername());
        assertEquals(Boolean.TRUE, captor.getValue().getStatus());
    }

    @Test
    void shouldForwardDeleteUserByPathId() {
        when(client.deleteUser(any())).thenReturn(true);

        Response<Boolean> response = controller.deleteUser(11L);

        ArgumentCaptor<DeleteRbacUserReq> captor = ArgumentCaptor.forClass(DeleteRbacUserReq.class);
        verify(client).deleteUser(captor.capture());
        assertEquals(11L, captor.getValue().getId());
        assertEquals(Boolean.TRUE, response.getData());
    }
}
