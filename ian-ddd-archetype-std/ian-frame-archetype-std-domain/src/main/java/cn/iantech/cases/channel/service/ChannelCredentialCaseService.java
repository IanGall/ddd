package cn.iantech.cases.channel.service;

import cn.iantech.cases.auth.model.AuthCaseModels;
import cn.iantech.cases.channel.model.ChannelCaseModels.*;
import cn.iantech.cases.model.Actor;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.channel.infra.IChannelDataScopeRepository;
import cn.iantech.domain.channel.model.ChannelCredentialEntity;
import cn.iantech.domain.channel.model.ChannelDataScope;
import cn.iantech.domain.channel.model.IssuedChannelCredential;
import cn.iantech.domain.channel.service.IChannelCredentialDomainService;
import cn.iantech.domain.model.DomainPage;
import cn.iantech.domain.rbac.service.IRbacAccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 渠道凭证管理用例，负责授权、事务与操作审计。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelCredentialCaseService {
    private static final Set<String> SCOPE_TYPES = Set.of("ACCOUNT", "TENANT", "STORE");

    private final IChannelCredentialDomainService domainService;
    private final IRbacAccessControlService accessControlService;
    private final IChannelDataScopeRepository dataScopeRepository;

    @Transactional(rollbackFor = Exception.class)
    public IssuedChannelCredential create(Actor actor, CreateCredential command) {
        requireRequest(command);
        authorize(actor, "create");
        IssuedChannelCredential issued = domainService.create(actor.userId(), command.channelName());
        audit(actor, "create", issued.credential());
        return issued;
    }

    public DomainPage<ChannelCredentialEntity> queryPage(Actor actor, QueryCredentialPage command) {
        authorize(actor, "read");
        QueryCredentialPage query = Objects.requireNonNullElseGet(command,
                () -> new QueryCredentialPage(null, null, null, null, null));
        return domainService.queryPage(Objects.requireNonNullElse(query.pageNum(), 1),
                Objects.requireNonNullElse(query.pageSize(), 20), query.channelCode(), query.channelName(), query.status());
    }

    public ChannelCredentialEntity queryById(Actor actor, Long id) {
        authorize(actor, "read");
        return domainService.queryById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelCredentialEntity update(Actor actor, UpdateCredential command) {
        requireRequest(command);
        authorize(actor, "update");
        ChannelCredentialEntity entity = domainService.update(actor.userId(), command.id(), command.channelName());
        audit(actor, "update", entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChannelCredentialEntity updateStatus(Actor actor, UpdateCredentialStatus command) {
        requireRequest(command);
        authorize(actor, "update");
        ChannelCredentialEntity entity = domainService.updateStatus(actor.userId(), command.id(), command.status());
        audit(actor, "updateStatus", entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public IssuedChannelCredential rotateSecret(Actor actor, Long id) {
        authorize(actor, "rotate");
        IssuedChannelCredential issued = domainService.rotateSecret(actor.userId(), id);
        audit(actor, "rotateSecret", issued.credential());
        return issued;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Actor actor, Long id) {
        authorize(actor, "delete");
        ChannelCredentialEntity entity = domainService.queryById(id);
        boolean deleted = domainService.delete(actor.userId(), id);
        audit(actor, "delete", entity);
        return deleted;
    }

    public List<ChannelDataScope> queryDataScopes(Actor actor, QueryDataScopes command) {
        requireRequest(command);
        authorize(actor, "read");
        String scopeType = requireScopeType(command.scopeType());
        domainService.queryById(command.channelId());
        return dataScopeRepository.findEnabledByChannelId(command.channelId(), scopeType);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ChannelDataScope> replaceDataScopes(Actor actor, ReplaceDataScopes command) {
        requireRequest(command);
        authorize(actor, "update");
        String scopeType = requireScopeType(command.scopeType());
        domainService.queryById(command.channelId());
        List<String> values = Objects.requireNonNullElse(command.scopeValues(), List.<String>of()).stream()
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                .filter(value -> value.length() <= 128).distinct().toList();
        dataScopeRepository.replace(command.channelId(), scopeType, values, actor.userId());
        return dataScopeRepository.findEnabledByChannelId(command.channelId(), scopeType);
    }

    private void authorize(Actor actor, String operation) {
        if (actor == null || !AuthCaseModels.SUBJECT_ADMIN_PRIMARY.equals(actor.subjectType())) {
            throw new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "仅平台主账号可管理外部渠道");
        }
        accessControlService.authorize(actor.accountId(), actor.userId(), actor.principalName(),
                "rbac:channel-credential:" + operation);
    }

    private void audit(Actor actor, String operation, ChannelCredentialEntity item) {
        log.info("渠道凭证操作审计，requestId={}, accountId={}, userId={}, operation={}, channelCredentialId={}, secretVersion={}",
                actor.requestId(), actor.accountId(), actor.userId(), operation, item.getId(), item.getSecretVersion());
    }

    private void requireRequest(Object command) {
        if (command == null) {
            throw new AppException(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), "请求不能为空");
        }
    }

    private String requireScopeType(String value) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_TYPES.contains(normalized)) {
            throw new AppException(Constants.ResponseCode.INVALID_ARGUMENT.getCode(),
                    "数据范围类型仅支持 ACCOUNT、TENANT、STORE");
        }
        return normalized;
    }
}
