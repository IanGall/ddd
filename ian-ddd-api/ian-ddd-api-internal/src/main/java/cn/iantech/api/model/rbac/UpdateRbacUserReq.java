package cn.iantech.api.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRbacUserReq implements Serializable {

    private static final long serialVersionUID = 1570448704667006823L;

    private Long id;

    private String password;

    private String displayName;

    private String email;

    private String mobile;

    private Boolean status;

}
