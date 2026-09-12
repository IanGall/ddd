package cn.iantech.api.model.rbac;

import cn.iantech.common.model.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QueryRbacPermissionPageReq extends PageRequest {

    private static final long serialVersionUID = -2899281682796292783L;

    private String permCode;

    private String permName;

    private Integer permType;

    private Long parentId;

    private Boolean status;

}
