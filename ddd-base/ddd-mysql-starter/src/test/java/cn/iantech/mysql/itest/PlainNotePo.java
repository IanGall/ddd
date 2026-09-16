package cn.iantech.mysql.itest;

/** 未声明 {@code @IdGenerator} 的测试实体：即使 INSERT 绑定了 id 列也不应被填充。 */
public class PlainNotePo {

    private Long id;

    private String body;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
