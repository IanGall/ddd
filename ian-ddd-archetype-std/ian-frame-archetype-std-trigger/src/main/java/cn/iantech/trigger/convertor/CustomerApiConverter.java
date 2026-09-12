package cn.iantech.trigger.convertor;

import cn.iantech.api.model.customer.CustomerUserDTO;
import cn.iantech.domain.customer.model.CustomerUserEntity;

/**
 * C 端用户领域实体与 RPC DTO 的边界转换器。
 */
public final class CustomerApiConverter {

    private CustomerApiConverter() {
    }

    public static CustomerUserDTO toDTO(CustomerUserEntity entity) {
        CustomerUserDTO dto = new CustomerUserDTO();
        dto.setId(entity.getId());
        dto.setLoginName(entity.getLoginName());
        dto.setDisplayName(entity.getDisplayName());
        dto.setAvatar(entity.getAvatar());
        dto.setStatus(entity.getStatus());
        return dto;
    }
}
