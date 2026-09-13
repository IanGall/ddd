package cn.iantech.api;

import cn.iantech.api.model.channel.*;
import cn.iantech.common.exception.AppException;

import java.util.List;

/**
 * 渠道长期凭证管理契约。
 */
public interface IChannelCredentialService {

    ChannelCredentialSecretDTO create(CreateChannelCredentialReq req) throws AppException;

    ChannelCredentialPageDTO queryPage(QueryChannelCredentialPageReq req) throws AppException;

    ChannelCredentialDTO queryById(QueryChannelCredentialByIdReq req) throws AppException;

    ChannelCredentialDTO update(UpdateChannelCredentialReq req) throws AppException;

    ChannelCredentialDTO updateStatus(UpdateChannelCredentialStatusReq req) throws AppException;

    ChannelCredentialSecretDTO rotateSecret(RotateChannelCredentialSecretReq req) throws AppException;

    Boolean delete(DeleteChannelCredentialReq req) throws AppException;

    List<ChannelDataScopeDTO> queryDataScopes(QueryChannelDataScopesReq req) throws AppException;

    List<ChannelDataScopeDTO> replaceDataScopes(ReplaceChannelDataScopesReq req) throws AppException;
}
