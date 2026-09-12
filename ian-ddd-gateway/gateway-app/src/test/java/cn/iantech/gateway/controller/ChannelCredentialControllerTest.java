package cn.iantech.gateway.controller;

import cn.iantech.api.model.channel.ChannelCredentialPageDTO;
import cn.iantech.api.model.channel.ChannelCredentialSecretDTO;
import cn.iantech.api.model.channel.CreateChannelCredentialReq;
import cn.iantech.api.model.channel.QueryChannelCredentialPageReq;
import cn.iantech.gateway.model.ChannelCredentialWebRequests;
import cn.iantech.gateway.service.GatewayChannelCredentialClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ChannelCredentialControllerTest {

    @Test
    void shouldMapCreateAndPageRequestsWithoutAcceptingAccountIdentity() {
        GatewayChannelCredentialClient client = mock(GatewayChannelCredentialClient.class);
        when(client.create(any())).thenReturn(ChannelCredentialSecretDTO.builder()
                .id(1L).channelCode("ch_abcdefghijklmnopqrstuv").channelSecret("secret").secretVersion(1L).build());
        when(client.queryPage(any())).thenReturn(ChannelCredentialPageDTO.builder().build());
        ChannelCredentialController controller = new ChannelCredentialController(client);

        controller.create(new ChannelCredentialWebRequests.Create("物流渠道"));
        controller.queryPage(1, 20, "ch_abcdefghijklmnopqrstuv", "物流", true);

        ArgumentCaptor<CreateChannelCredentialReq> create = ArgumentCaptor.forClass(CreateChannelCredentialReq.class);
        verify(client).create(create.capture());
        assertEquals("物流渠道", create.getValue().getChannelName());
        ArgumentCaptor<QueryChannelCredentialPageReq> page = ArgumentCaptor.forClass(QueryChannelCredentialPageReq.class);
        verify(client).queryPage(page.capture());
        assertEquals(1, page.getValue().getPageNum());
        assertEquals("ch_abcdefghijklmnopqrstuv", page.getValue().getChannelCode());
    }
}
