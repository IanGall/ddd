package cn.iantech.cases.rbac.service;

import cn.iantech.cases.model.Actor;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.auth.service.PasswordPolicy;
import cn.iantech.domain.model.DomainPage;
import cn.iantech.domain.rbac.model.RbacPermissionCode;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import cn.iantech.domain.rbac.service.impl.RbacAccessControlService;
import cn.iantech.domain.rbac.service.impl.RbacAccountService;
import cn.iantech.domain.rbac.service.impl.RbacDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

import static cn.iantech.cases.rbac.model.RbacCaseCommands.*;

/**
 * RBAC 用例编排，统一维护权限前置校验、审计和事务边界。
 *
 * <p><b>口令相关的入口使用 {@link TransactionTemplate} 显式收窄事务范围</b>：BCrypt 约 100ms，
 * 若与落库同处一个事务会长时间占用连接池连接。因此「校验 → 编码」在事务外完成，事务只包住
 * 数据库写入；其余不涉及慢哈希的入口继续使用 {@code @Transactional}。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbacCaseService {
    private final RbacDomainService rbacDomainService;
    private final RbacAccountService accountService;
    private final RbacAccessControlService accessControlService;
    private final IPasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    /**
     * 开户：口令校验与编码在事务外完成，事务只包住「账号落库 + 内置权限初始化」。
     */
    public AccountResult createAccount(CreateAccount command) {
        requireRequest(command);
        PasswordPolicy.check(command.password());
        String passwordHash = encodeOutsideTransaction(command.password());
        RbacAccountEntity account = transactionTemplate.execute(status -> accountService.createAccount(
                command.username(), passwordHash, command.displayName(), command.email(), command.mobile()));
        if (account == null) {
            throw new IllegalStateException("开户事务未返回账号结果");
        }
        return new AccountResult(account.getId(), account.getUsername(),
                account.getUsername() + "@" + account.getId() + ".com");
    }

    /**
     * 建子账号：<b>授权先于哈希</b>，避免无权限请求也付出约 100ms 的编码代价。
     */
    public RbacUserEntity createUser(Actor actor, CreateUser command) {
        authorize(actor, RbacPermissionCode.USER_CREATE);
        requireRequest(command);
        PasswordPolicy.check(command.password());
        String passwordHash = encodeOutsideTransaction(command.password());
        return transactionTemplate.execute(status -> rbacDomainService.createUser(actor.accountId(),
                command.username(), passwordHash, command.displayName(), command.email(), command.mobile(),
                command.status()));
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

    /**
     * 改子账号：空白口令表示<b>不改密</b>——不校验、不编码，向写入阶段传 {@code null}。
     */
    public RbacUserEntity updateUser(Actor actor, UpdateUser command) {
        authorize(actor, RbacPermissionCode.USER_UPDATE);
        requireRequest(command);
        String passwordHash = encodePasswordIfPresent(command.password());
        return transactionTemplate.execute(status -> rbacDomainService.updateUser(actor.accountId(), command.id(),
                passwordHash, command.displayName(), command.email(), command.mobile(), command.status()));
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

    /**
     * 查询当前主体的有效权限码（去重、升序）。
     *
     * <p><b>刻意不做 RBAC 权限前置校验</b>：本接口是前端菜单与按钮的权限引导来源，若要求调用者
     * 自身持有某个权限码会形成循环依赖——没有 RBAC 读权限的子账号将无法加载自己的权限集合
     * （表现为前端菜单全空）。账号有效性仍由领域服务校验，因此停用账号即时失效。</p>
     */
    public List<String> queryOwnPermissionCodes(Actor actor) {
        if (actor == null) {
            throw new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "无权访问");
        }
        return accessControlService.findEffectivePermissionCodes(actor.accountId(), actor.userId(),
                actor.principalName());
    }

    /**
     * 空白口令表示不改密，返回 {@code null}；否则先校验长度再编码。
     */
    private String encodePasswordIfPresent(String rawPassword) {
        if (StringUtils.isBlank(rawPassword)) {
            return null;
        }
        PasswordPolicy.check(rawPassword);
        return encodeOutsideTransaction(rawPassword);
    }

    /**
     * 口令编码必须在事务外执行：BCrypt 约 100ms，进入事务会长时间占用连接池连接。
     *
     * <p>若调用方已开启外层事务则直接拒绝，而不是静默地在事务内编码——本入口明确不支持
     * 嵌套在外层事务中调用；将来若确有该需求，应另行评估传播语义，而不是就地放开。</p>
     */
    private String encodeOutsideTransaction(String rawPassword) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("口令编码不得在事务边界内执行");
        }
        return passwordEncoder.encode(rawPassword);
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
