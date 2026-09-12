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
public class QueryRbacUserPageReq extends PageRequest {

    private static final long serialVersionUID = 4984777031809366275L;

    private String username;

    private Boolean status;

}
