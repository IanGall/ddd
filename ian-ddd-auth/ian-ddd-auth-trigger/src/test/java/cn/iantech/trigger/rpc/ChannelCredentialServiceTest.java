package cn.iantech.trigger.rpc;

import cn.iantech.api.model.channel.ChannelCredentialPageDTO;
import cn.iantech.api.model.channel.ChannelCredentialSecretDTO;
import cn.iantech.api.model.channel.ChannelDataScopeDTO;
import cn.iantech.api.model.channel.CreateChannelCredentialReq;
import cn.iantech.api.model.channel.QueryChannelCredentialPageReq;
import cn.iantech.api.model.channel.QueryChannelDataScopesReq;
import cn.iantech.cases.channel.model.ChannelCaseModels.CreateCredential;
import cn.iantech.cases.channel.model.ChannelCaseModels.QueryCredentialPage;
import cn.iantech.cases.channel.model.ChannelCaseModels.QueryDataScopes;
import cn.iantech.cases.channel.service.ChannelCredentialCaseService;
import cn.iantech.cases.model.Actor;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.channel.model.ChannelDataScope;
import cn.iantech.domain.channel.model.IssuedChannelCredential;
import cn.iantech.domain.model.DomainPage;
import cn.iantech.trigger.context.ActorResolver;
import cn.iantech.trigger.convertor.ChannelCredentialCommandConvertor;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 渠道凭证 Dubbo 入站适配器：可信 Actor 解析、命令映射与 DTO 转换。
 *
 * <p>入站适配器最关键的不变量是「解析出的 Actor 必须原样传入用例层」——
 * cases 的鉴权与审计都依赖它，因此每个用例都断言 Actor 透传。</p>
 */
class ChannelCredentialServiceTest {

    private static final Actor ACTOR = new Actor("req-1", 1001L, 2002L, "alice", "ADMIN", "gateway");
    private static final ChannelCredentialEntity CREDENTIAL = ChannelCredentialEntity.builder()
            .id(77L).channelCode("ch_Abcdefghijklmnopqrstuv").channelName("示例渠道")
            .secretVersion(2L).status(Boolean.TRUE).build();

    private final ChannelCredentialCaseService caseService = mock(ChannelCredentialCaseService.class);
    private final ActorResolver actorResolver = mock(ActorResolver.class);
    private final ChannelCredentialCommandConvertor commandConvertor =
            Mappers.getMapper(ChannelCredentialCommandConvertor.class);
    private final ChannelCredentialService service =
            new ChannelCredentialService(caseService, actorResolver, commandConvertor);

    @Test
    void shouldResolveActorAndMapSecretOnCreate() {
        when(actorResolver.resolve()).thenReturn(ACTOR);
        when(caseService.create(any(), any())).thenReturn(new IssuedChannelCredential(CREDENTIAL, "plain-secret"));

        ChannelCredentialSecretDTO dto = service.create(
                CreateChannelCredentialReq.builder().channelName("示例渠道").build());

        assertEquals(77L, dto.getId());
        assertEquals("ch_Abcdefghijklmnopqrstuv", dto.getChannelCode());
        assertEquals("plain-secret", dto.getChannelSecret());
        assertEquals(2L, dto.getSecretVersion());

        ArgumentCaptor<CreateCredential> captor = ArgumentCaptor.forClass(CreateCredential.class);
        verify(caseService).create(eq(ACTOR), captor.capture());
        assertEquals("示例渠道", captor.getValue().channelName());
    }

    @Test
    void shouldMapPageNumbersAndConvertListOnQueryPage() {
        when(actorResolver.resolve()).thenReturn(ACTOR);
        when(caseService.queryPage(any(), any()))
                .thenReturn(new DomainPage<>(1L, 2, 20, List.of(CREDENTIAL)));

        ChannelCredentialPageDTO dto = service.queryPage(
                QueryChannelCredentialPageReq.builder().pageNum(2).pageSize(20).build());

        assertEquals(1L, dto.getTotal());
        assertEquals(2, dto.getPageNum());
        assertEquals(20, dto.getPageSize());
        assertEquals(1, dto.getList().size());
        assertEquals(77L, dto.getList().getFirst().getId());

        ArgumentCaptor<QueryCredentialPage> captor = ArgumentCaptor.forClass(QueryCredentialPage.class);
        verify(caseService).queryPage(eq(ACTOR), captor.capture());
        assertEquals(2, captor.getValue().pageNum());
        assertEquals(20, captor.getValue().pageSize());
    }

    @Test
    void shouldPassNullIdThroughOnDelete() {
        when(actorResolver.resolve()).thenReturn(ACTOR);
        when(caseService.delete(any(), any())).thenReturn(Boolean.TRUE);

        assertTrue(service.delete(null));

        verify(caseService).delete(ACTOR, null);
    }

    @Test
    void shouldConvertDataScopesAndKeepActor() {
        when(actorResolver.resolve()).thenReturn(ACTOR);
        when(caseService.queryDataScopes(any(), any())).thenReturn(List.of(
                ChannelDataScope.builder().channelId(77L).scopeType("REGION").scopeValue("CN").build()));

        List<ChannelDataScopeDTO> scopes = service.queryDataScopes(
                QueryChannelDataScopesReq.builder().channelId(77L).scopeType("REGION").build());

        assertEquals(1, scopes.size());
        assertEquals("REGION", scopes.getFirst().getScopeType());
        assertEquals("CN", scopes.getFirst().getScopeValue());

        ArgumentCaptor<QueryDataScopes> captor = ArgumentCaptor.forClass(QueryDataScopes.class);
        verify(caseService).queryDataScopes(eq(ACTOR), captor.capture());
        assertEquals(77L, captor.getValue().channelId());
    }
}
