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
public class DeleteRbacPermissionReq implements Serializable {

    private static final long serialVersionUID = -3189452114861670576L;

    private Long id;

}
