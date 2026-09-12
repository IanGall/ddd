package cn.iantech.trigger.convertor;

import cn.iantech.api.model.rbac.RbacUserDTO;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import io.github.linpeilie.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RbacUserEntityMapper extends BaseMapper<RbacUserEntity, RbacUserDTO> {
}
