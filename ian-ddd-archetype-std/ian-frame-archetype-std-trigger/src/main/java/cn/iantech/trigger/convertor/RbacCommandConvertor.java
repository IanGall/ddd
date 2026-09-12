package cn.iantech.trigger.convertor;

import cn.iantech.api.model.rbac.*;
import cn.iantech.cases.rbac.model.RbacCaseCommands;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * RBAC 请求 DTO 到用例命令的映射，MapStruct 生成实现保证字段同步与 null 源透传。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RbacCommandConvertor {

    RbacCaseCommands.CreateUser toCommand(CreateRbacUserReq req);

    RbacCaseCommands.UpdateUser toCommand(UpdateRbacUserReq req);

    RbacCaseCommands.QueryUserPage toCommand(QueryRbacUserPageReq req);

    RbacCaseCommands.CreateRole toCommand(CreateRbacRoleReq req);

    RbacCaseCommands.UpdateRole toCommand(UpdateRbacRoleReq req);

    RbacCaseCommands.QueryRolePage toCommand(QueryRbacRolePageReq req);

    RbacCaseCommands.CreatePermission toCommand(CreateRbacPermissionReq req);

    RbacCaseCommands.UpdatePermission toCommand(UpdateRbacPermissionReq req);

    RbacCaseCommands.QueryPermissionPage toCommand(QueryRbacPermissionPageReq req);

    RbacCaseCommands.ReplaceUserRoles toCommand(ReplaceUserRolesReq req);

    RbacCaseCommands.ReplaceRolePermissions toCommand(ReplaceRolePermissionsReq req);
}
