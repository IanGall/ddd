package cn.iantech.cases.rbac.service;

import cn.iantech.cases.model.Actor;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.model.DomainPage;
import cn.iantech.domain.rbac.model.RbacPermissionCode;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import cn.iantech.domain.rbac.service.IRbacAccessControlService;
import cn.iantech.domain.rbac.service.IRbacDomainService;
import cn.iantech.domain.rbac.service.impl.RbacAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iantech.cases.rbac.model.RbacCaseCommands.*;

/**
 * RBAC 用例编排，统一维护权限前置校验、审计和事务边界。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbacCaseService {
    private final IRbacDomainService rbacDomainService;
    private final RbacAccountService accountService;
    private final IRbacAccessControlService accessControlService;

    @Transactional(rollbackFor = Exception.class)
    public AccountResult createAccount(CreateAccount command) {
        requireRequest(command);
        var account = accountService.createAccount(command.username(), command.password(), command.displayName(),
                command.email(), command.mobile());
        return new AccountResult(account.getId(), account.getUsername(),
                account.getUsername() + "@" + account.getId() + ".com");
    }

    @Transactional(rollbackFor = Exception.class)
    public RbacUserEntity createUser(Actor actor, CreateUser command) {
        authorize(actor, RbacPermissionCode.USER_CREATE);
        requireRequest(command);
        return rbacDomainService.createUser(actor.accountId(), command.username(), command.password(),
                command.displayName(), command.email(), command.mobile(), command.status());
    }

    public RbacUserEntity queryUserById(Actor actor, Long id) {
        authorize(actor, RbacPermissionCode.USER_READ);
        return rbacDomainService.queryUserById(actor.accountId(), id);
    }

    public DomainPage<RbacUserEntity> queryUserPage(Actor actor, QueryUserPage command) {
        authorize(actor, RbacPermissionCode.USER_READ);
        QueryUserPage query = Objects.requireNonNullElseGet(command,
                () -> new QueryUserPage(null, null, null, null));
        return rbacDomainService.queryUserPage(actor.accountId(), query.pageNum(), query.pageSize(),
                query.username(), query.status());
    }

    @Transactional(rollbackFor = Exception.class)
    public RbacUserEntity updateUser(Actor actor, UpdateUser command) {
        authorize(actor, RbacPermissionCode.USER_UPDATE);
        requireRequest(command);
        return rbacDomainService.updateUser(actor.accountId(), command.id(), command.password(), command.displayName(),
                command.email(), command.mobile(), command.status());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Actor actor, Long id) {
        authorize(actor, RbacPermissionCode.USER_DELETE);
        return rbacDomainService.deleteUser(actor.accountId(), id);
    }

    @Transactional(rollbackFor = Exception.class)
    public RbacRoleEntity createRole(Actor actor, CreateRole command) {
        authorize(actor, RbacPermissionCode.ROLE_CREATE);
        requireRequest(command);
        return rbacDomainService.createRole(actor.accountId(), command.roleCode(), command.roleName(),
                command.roleDesc(), command.status());
    }

    public RbacRoleEntity queryRoleById(Actor actor, Long id) {
        authorize(actor, RbacPermissionCode.ROLE_READ);
        return rbacDomainService.queryRoleById(actor.accountId(), id);
    }

    public DomainPage<RbacRoleEntity> queryRolePage(Actor actor, QueryRolePage command) {
        authorize(actor, RbacPermissionCode.ROLE_READ);
        QueryRolePage query = Objects.requireNonNullElseGet(command,
                () -> new QueryRolePage(null, null, null, null, null));
        return rbacDomainService.queryRolePage(actor.accountId(), query.pageNum(), query.pageSize(), query.roleCode(),
                query.roleName(), query.status());
    }

    @Transactional(rollbackFor = Exception.class)
    public RbacRoleEntity updateRole(Actor actor, UpdateRole command) {
        authorize(actor, RbacPermissionCode.ROLE_UPDATE);
        requireRequest(command);
        return rbacDomainService.updateRole(actor.accountId(), command.id(), command.roleCode(), command.roleName(),
                command.roleDesc(), command.status());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(Actor actor, Long id) {
        authorize(actor, RbacPermissionCode.ROLE_DELETE);
        return rbacDomainService.deleteRole(actor.accountId(), id);
    }

    @Transactional(rollbackFor = Exception.class)
    public RbacPermissionEntity createPermission(Actor actor, CreatePermission command) {
        authorize(actor, RbacPermissionCode.PERMISSION_CREATE);
        requireRequest(command);
        return rbacDomainService.createPermission(actor.accountId(), command.permCode(), command.permName(),
                command.permType(), command.parentId(), command.path(), command.method(), command.status());
    }

    public RbacPermissionEntity queryPermissionById(Actor actor, Long id) {
        authorize(actor, RbacPermissionCode.PERMISSION_READ);
        return rbacDomainService.queryPermissionById(actor.accountId(), id);
    }

    public DomainPage<RbacPermissionEntity> queryPermissionPage(Actor actor, QueryPermissionPage command) {
        authorize(actor, RbacPermissionCode.PERMISSION_READ);
        QueryPermissionPage query = Objects.requireNonNullElseGet(command,
                () -> new QueryPermissionPage(null, null, null, null, null, null, null));
        return rbacDomainService.queryPermissionPage(actor.accountId(), query.pageNum(), query.pageSize(),
                query.permCode(), query.permName(), query.permType(), query.parentId(), query.status());
    }

    @Transactional(rollbackFor = Exception.class)
    public RbacPermissionEntity updatePermission(Actor actor, UpdatePermission command) {
        authorize(actor, RbacPermissionCode.PERMISSION_UPDATE);
        requireRequest(command);
        accessControlService.authorizePermissionManagement(actor.accountId(), actor.userId(),
                actor.principalName(), command.id());
        return rbacDomainService.updatePermission(actor.accountId(), command.id(), command.permName(),
                command.permType(), command.parentId(), command.path(), command.method(), command.status());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deletePermission(Actor actor, Long id) {
        authorize(actor, RbacPermissionCode.PERMISSION_DELETE);
        accessControlService.authorizePermissionManagement(actor.accountId(), actor.userId(), actor.principalName(), id);
        return rbacDomainService.deletePermission(actor.accountId(), id);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean replaceUserRoles(Actor actor, ReplaceUserRoles command) {
        authorize(actor, RbacPermissionCode.USER_ROLE_GRANT);
        requireRequest(command);
        accessControlService.authorizeRoleGrant(actor.accountId(), actor.userId(), actor.principalName(),
                command.roleIds());
        return rbacDomainService.replaceUserRoles(actor.accountId(), command.userId(), command.roleIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean replaceRolePermissions(Actor actor, ReplaceRolePermissions command) {
        authorize(actor, RbacPermissionCode.ROLE_PERMISSION_GRANT);
        requireRequest(command);
        accessControlService.authorizePermissionGrant(actor.accountId(), actor.userId(), actor.principalName(),
                command.permissionIds());
        return rbacDomainService.replaceRolePermissions(actor.accountId(), command.roleId(), command.permissionIds());
    }

    public List<Long> queryUserRoleIds(Actor actor, Long userId) {
        authorize(actor, RbacPermissionCode.USER_ROLE_READ);
        return rbacDomainService.queryUserRoleIds(actor.accountId(), userId);
    }

    public List<Long> queryRolePermissionIds(Actor actor, Long roleId) {
        authorize(actor, RbacPermissionCode.ROLE_PERMISSION_READ);
        return rbacDomainService.queryRolePermissionIds(actor.accountId(), roleId);
    }

    private void requireRequest(Object command) {
        if (command == null) {
            throw new AppException(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), "请求不能为空");
        }
    }

    private void authorize(Actor actor, RbacPermissionCode permission) {
        if (actor == null) {
            throw new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "无权访问");
        }
        long started = System.nanoTime();
        try {
            accessControlService.authorize(actor.accountId(), actor.userId(), actor.principalName(), permission);
            // 全量放行日志量过大（每个 RPC 请求一条），降为 DEBUG；拒绝仍保持 WARN 便于安全审计
            log.debug("RBAC 授权审计，requestId={}, accountId={}, userId={}, subjectType={}, resource={}, action={}, permission={}, risk={}, result=ALLOW, elapsedMicros={}",
                    actor.requestId(), actor.accountId(), actor.userId(), actor.subjectType(), permission.getResource(),
                    permission.getAction(), permission.getCode(), permission.getRiskLevel(),
                    (System.nanoTime() - started) / 1_000);
        } catch (RuntimeException exception) {
            log.warn("RBAC 授权审计，requestId={}, accountId={}, userId={}, subjectType={}, resource={}, action={}, permission={}, risk={}, result=DENY, reason={}, elapsedMicros={}",
                    actor.requestId(), actor.accountId(), actor.userId(), actor.subjectType(), permission.getResource(),
                    permission.getAction(), permission.getCode(), permission.getRiskLevel(),
                    exception.getClass().getSimpleName(), (System.nanoTime() - started) / 1_000);
            throw exception;
        }
    }
}
