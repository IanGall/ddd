package cn.iantech.api.external.sample;

import cn.iantech.api.external.sample.model.SampleResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 外部 HTTP 契约示例（骨架占位，可删除或用真实外部服务契约替换）。
 *
 * <p>演示外部 API 的写法：类级 {@link HttpExchange} 声明基础路径，方法级注解声明具体调用。</p>
 */
@HttpExchange(url = "/api/external/sample", accept = "application/json")
public interface SampleExternalApi {

    /**
     * 按 ID 查询示例资源。
     *
     * @param id 资源 ID
     * @return 示例响应
     */
    @GetExchange("/{id}")
    SampleResponse getById(@PathVariable("id") String id);
}
