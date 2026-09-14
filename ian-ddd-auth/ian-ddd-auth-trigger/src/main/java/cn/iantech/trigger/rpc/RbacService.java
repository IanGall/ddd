package cn.iantech.trigger.rpc;

import cn.iantech.api.IRbacService;
import cn.iantech.api.model.rbac.*;
import cn.iantech.cases.model.Actor;
import cn.iantech.cases.rbac.model.RbacCaseCommands;
import cn.iantech.cases.rbac.service.RbacCaseService;
import cn.iantech.domain.model.DomainPage;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import cn.iantech.trigger.context.ActorResolver;
import cn.iantech.trigger.convertor.RbacCommandConvertor;
import io.github.linpeilie.Converter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo 入站适配器，负责 API DTO、可信 Actor 与 RBAC 用例模型之间的转换。
 */
@DubboService(version = "1.0.0", protocol = "dubbo", timeout = 3000)
@RequiredArgsConstructor
public class RbacService implements IRbacService {

    private final RbacCaseService rbacCaseService;
    private final ActorResolver actorResolver;
    private final Converter converter;
    private final RbacCommandConvertor commandConvertor;

    @Override
    public RbacUserDTO createUser(CreateRbacUserReq req) {
        RbacCaseCommands.CreateUser command = commandConvertor.toCommand(req);
        return converter.convert(rbacCaseService.createUser(actor(), command), RbacUserDTO.class);
    }

    @Override
    public RbacUserDTO queryUserById(Long id) {
        return converter.convert(rbacCaseService.queryUserById(actor(), id), RbacUserDTO.class);
    }

    @Override
    public RbacUserPageDTO queryUserPage(QueryRbacUserPageReq req) {
        RbacCaseCommands.QueryUserPage command = commandConvertor.toCommand(req);
        DomainPage<RbacUserEntity> page = rbacCaseService.queryUserPage(actor(), command);
        return RbacUserPageDTO.builder().total(page.total()).pageNum(page.pageNum()).pageSize(page.pageSize())
                .list(converter.convert(page.list(), RbacUserDTO.class)).build();
    }

    @Override
    public RbacUserDTO updateUser(UpdateRbacUserReq req) {
        RbacCaseCommands.UpdateUser command = commandConvertor.toCommand(req);
        return converter.convert(rbacCaseService.updateUser(actor(), command), RbacUserDTO.class);
    }

    @Override
    public Boolean deleteUser(DeleteRbacUserReq req) {
        return rbacCaseService.deleteUser(actor(), req == null ? null : req.getId());
    }

    @Override
    public RbacRoleDTO createRole(CreateRbacRoleReq req) {
        RbacCaseCommands.CreateRole command = commandConvertor.toCommand(req);
        return converter.convert(rbacCaseService.createRole(actor(), command), RbacRoleDTO.class);
    }

    @Override
    public RbacRoleDTO queryRoleById(Long id) {
        return converter.convert(rbacCaseService.queryRoleById(actor(), id), RbacRoleDTO.class);
    }

    @Override
    public RbacRolePageDTO queryRolePage(QueryRbacRolePageReq req) {
        RbacCaseCommands.QueryRolePage command = commandConvertor.toCommand(req);
        DomainPage<RbacRoleEntity> page = rbacCaseService.queryRolePage(actor(), command);
        return RbacRolePageDTO.builder().total(page.total()).pageNum(page.pageNum()).pageSize(page.pageSize())
                .list(converter.convert(page.list(), RbacRoleDTO.class)).build();
    }

    @Override
    public RbacRoleDTO updateRole(UpdateRbacRoleReq req) {
        RbacCaseCommands.UpdateRole command = commandConvertor.toCommand(req);
        return converter.convert(rbacCaseService.updateRole(actor(), command), RbacRoleDTO.class);
    }

    @Override
    public Boolean deleteRole(DeleteRbacRoleReq req) {
        return rbacCaseService.deleteRole(actor(), req == null ? null : req.getId());
    }

    @Override
    public RbacPermissionDTO createPermission(CreateRbacPermissionReq req) {
        RbacCaseCommands.CreatePermission command = commandConvertor.toCommand(req);
        return converter.convert(rbacCaseService.createPermission(actor(), command), RbacPermissionDTO.class);
    }

    @Override
    public RbacPermissionDTO queryPermissionById(Long id) {
        return converter.convert(rbacCaseService.queryPermissionById(actor(), id), RbacPermissionDTO.class);
    }

    @Override
    public RbacPermissionPageDTO queryPermissionPage(QueryRbacPermissionPageReq req) {
        RbacCaseCommands.QueryPermissionPage command = commandConvertor.toCommand(req);
        DomainPage<RbacPermissionEntity> page = rbacCaseService.queryPermissionPage(actor(), command);
        return RbacPermissionPageDTO.builder().total(page.total()).pageNum(page.pageNum()).pageSize(page.pageSize())
                .list(converter.convert(page.list(), RbacPermissionDTO.class)).build();
    }

    @Override
    public RbacPermissionDTO updatePermission(UpdateRbacPermissionReq req) {
        RbacCaseCommands.UpdatePermission command = commandConvertor.toCommand(req);
        return converter.convert(rbacCaseService.updatePermission(actor(), command), RbacPermissionDTO.class);
    }

    @Override
    public Boolean deletePermission(DeleteRbacPermissionReq req) {
        return rbacCaseService.deletePermission(actor(), req == null ? null : req.getId());
    }

    @Override
    public Boolean replaceUserRoles(ReplaceUserRolesReq req) {
        RbacCaseCommands.ReplaceUserRoles command = commandConvertor.toCommand(req);
        return rbacCaseService.replaceUserRoles(actor(), command);
    }

    @Override
    public Boolean replaceRolePermissions(ReplaceRolePermissionsReq req) {
        RbacCaseCommands.ReplaceRolePermissions command = commandConvertor.toCommand(req);
        return rbacCaseService.replaceRolePermissions(actor(), command);
    }

    @Override
    public QueryUserRoleIdsResp queryUserRoleIds(QueryUserRoleIdsReq req) {
        Long userId = req == null ? null : req.getUserId();
        return QueryUserRoleIdsResp.builder().userId(userId)
                .roleIds(rbacCaseService.queryUserRoleIds(actor(), userId)).build();
    }

    @Override
    public QueryRolePermissionIdsResp queryRolePermissionIds(QueryRolePermissionIdsReq req) {
        Long roleId = req == null ? null : req.getRoleId();
        return QueryRolePermissionIdsResp.builder().roleId(roleId)
                .permissionIds(rbacCaseService.queryRolePermissionIds(actor(), roleId)).build();
    }

    private Actor actor() {
        return actorResolver.resolve();
    }
}
