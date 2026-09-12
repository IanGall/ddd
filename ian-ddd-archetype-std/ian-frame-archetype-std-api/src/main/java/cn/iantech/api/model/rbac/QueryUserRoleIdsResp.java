package cn.iantech.api.model.rbac;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryUserRoleIdsResp implements Serializable {

    private static final long serialVersionUID = 6899255143119714122L;

    private Long userId;

    private List<Long> roleIds;

}
