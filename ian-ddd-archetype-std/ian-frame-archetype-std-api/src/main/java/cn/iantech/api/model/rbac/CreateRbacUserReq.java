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
public class CreateRbacUserReq implements Serializable {

    private static final long serialVersionUID = 3821770448552612128L;

    private String username;

    private String password;

    private String displayName;

    private String email;

    private String mobile;

    private Boolean status;

}
