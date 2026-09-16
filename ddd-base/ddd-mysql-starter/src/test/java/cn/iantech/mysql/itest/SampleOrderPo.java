package cn.iantech.mysql.itest;

import cn.iantech.mysql.annotation.IdGenerator;

/** 声明了 {@code @IdGenerator} 的测试实体。 */
@IdGenerator("identity")
public class SampleOrderPo {

    private Long id;

    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
