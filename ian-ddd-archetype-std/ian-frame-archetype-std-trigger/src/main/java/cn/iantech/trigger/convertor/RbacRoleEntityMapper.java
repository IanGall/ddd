package cn.iantech.trigger.convertor;

import cn.iantech.api.model.rbac.RbacRoleDTO;
import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RbacRoleEntityMapper extends BaseMapper<RbacRoleEntity, RbacRoleDTO> {
}
