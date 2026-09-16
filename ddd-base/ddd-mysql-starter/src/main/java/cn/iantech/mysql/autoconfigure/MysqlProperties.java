package cn.iantech.mysql.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyBatis 主键自动填充配置。
 */
@ConfigurationProperties(prefix = "ddd.mysql")
public class MysqlProperties {

    /** 关闭后不注册主键填充拦截器，容器中也不会有对应 Bean。 */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
