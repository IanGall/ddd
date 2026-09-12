package cn.iantech.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BasePO implements Serializable {

    private static final long serialVersionUID = -8885558994220155338L;

    private Long id;

    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
