package cn.iantech.api.model.customer;

import lombok.Data;

import java.io.Serializable;

@Data
public class CustomerUserDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String loginName;
    private String displayName;
    private String avatar;
    private Boolean status;
}
