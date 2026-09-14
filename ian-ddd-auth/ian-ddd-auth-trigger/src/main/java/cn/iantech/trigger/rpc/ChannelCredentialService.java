package cn.iantech.trigger.rpc;

import cn.iantech.api.IChannelCredentialService;
import cn.iantech.api.model.channel.*;
import cn.iantech.cases.channel.model.ChannelCaseModels.*;
import cn.iantech.cases.channel.service.ChannelCredentialCaseService;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.model.DomainPage;
import cn.iantech.trigger.context.ActorResolver;
import cn.iantech.trigger.convertor.ChannelApiConverter;
import cn.iantech.trigger.convertor.ChannelCredentialCommandConvertor;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 渠道凭证管理 Dubbo 入站适配器。
 */
@DubboService(version = "1.0.0", protocol = "dubbo", timeout = 3000)
@RequiredArgsConstructor
public class ChannelCredentialService implements IChannelCredentialService {
    private final ChannelCredentialCaseService channelCredentialCaseService;
    private final ActorResolver actorResolver;
    private final ChannelCredentialCommandConvertor commandConvertor;

    @Override
    public ChannelCredentialSecretDTO create(CreateChannelCredentialReq req) {
        CreateCredential command = commandConvertor.toCommand(req);
        return ChannelApiConverter.toSecretDTO(channelCredentialCaseService.create(actorResolver.resolve(), command));
    }

    @Override
    public ChannelCredentialPageDTO queryPage(QueryChannelCredentialPageReq req) {
        QueryCredentialPage command = commandConvertor.toCommand(req);
        DomainPage<ChannelCredentialEntity> page =
                channelCredentialCaseService.queryPage(actorResolver.resolve(), command);
        return ChannelCredentialPageDTO.builder().total(page.total()).pageNum(page.pageNum()).pageSize(page.pageSize())
                .list(page.list().stream().map(ChannelApiConverter::toDTO).toList()).build();
    }

    @Override
    public ChannelCredentialDTO queryById(QueryChannelCredentialByIdReq req) {
        return ChannelApiConverter.toDTO(channelCredentialCaseService.queryById(actorResolver.resolve(),
                req == null ? null : req.getId()));
    }

    @Override
    public ChannelCredentialDTO update(UpdateChannelCredentialReq req) {
        UpdateCredential command = commandConvertor.toCommand(req);
        return ChannelApiConverter.toDTO(channelCredentialCaseService.update(actorResolver.resolve(), command));
    }

    @Override
    public ChannelCredentialDTO updateStatus(UpdateChannelCredentialStatusReq req) {
        UpdateCredentialStatus command = commandConvertor.toCommand(req);
        return ChannelApiConverter.toDTO(channelCredentialCaseService.updateStatus(actorResolver.resolve(), command));
    }

    @Override
    public ChannelCredentialSecretDTO rotateSecret(RotateChannelCredentialSecretReq req) {
        return ChannelApiConverter.toSecretDTO(channelCredentialCaseService.rotateSecret(actorResolver.resolve(),
                req == null ? null : req.getId()));
    }

    @Override
    public Boolean delete(DeleteChannelCredentialReq req) {
        return channelCredentialCaseService.delete(actorResolver.resolve(), req == null ? null : req.getId());
    }

    @Override
    public List<ChannelDataScopeDTO> queryDataScopes(QueryChannelDataScopesReq req) {
        QueryDataScopes command = commandConvertor.toCommand(req);
        return channelCredentialCaseService.queryDataScopes(actorResolver.resolve(), command).stream()
                .map(ChannelApiConverter::toScopeDTO).toList();
    }

    @Override
    public List<ChannelDataScopeDTO> replaceDataScopes(ReplaceChannelDataScopesReq req) {
        ReplaceDataScopes command = commandConvertor.toCommand(req);
        return channelCredentialCaseService.replaceDataScopes(actorResolver.resolve(), command).stream()
                .map(ChannelApiConverter::toScopeDTO).toList();
    }
}
