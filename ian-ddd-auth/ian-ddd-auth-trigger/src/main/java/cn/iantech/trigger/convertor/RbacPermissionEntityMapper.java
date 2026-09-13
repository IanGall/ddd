package cn.iantech.trigger.convertor;

import cn.iantech.api.model.rbac.RbacPermissionDTO;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RbacPermissionEntityMapper extends BaseMapper<RbacPermissionEntity, RbacPermissionDTO> {
}
