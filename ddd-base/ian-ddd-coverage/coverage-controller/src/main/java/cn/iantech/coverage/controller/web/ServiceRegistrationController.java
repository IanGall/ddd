package cn.iantech.coverage.controller.web;

import cn.iantech.coverage.controller.core.CoverageModels;
import cn.iantech.coverage.controller.core.DynamicServiceRegistrar;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 运行时服务注册接口：新增被测服务不需要修改配置文件并重启控制器。
 *
 * <pre>
 * POST   /api/coverage/services          注册（或 replace=true 覆盖）一个服务
 * GET    /api/coverage/services          列出当前生效的全部服务
 * DELETE /api/coverage/services/{name}   注销运行时注册的服务
 * </pre>
 *
 * <p>注册结果会落盘到 {@code <工作目录>/services/}，控制器重启后自动恢复。</p>
 */
@RestController
@RequestMapping("/api/coverage/services")
public class ServiceRegistrationController {

    private final DynamicServiceRegistrar registrar;

    public ServiceRegistrationController(DynamicServiceRegistrar registrar) {
        this.registrar = registrar;
    }

    /**
     * 注册一个服务。已存在时返回 409，除非请求里 {@code replace=true}。
     */
    @PostMapping
    public CoverageModels.ServiceView register(
            @RequestBody CoverageModels.ServiceRegistrationRequest request) {
        return registrar.register(request);
    }

    /**
     * 列出当前生效的服务，含来源标记与目录告警。
     */
    @GetMapping
    public List<CoverageModels.ServiceView> list() {
        return registrar.list();
    }

    /**
     * 注销运行时注册的服务；配置文件中的服务返回 400。
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> unregister(@PathVariable String name) {
        boolean removed = registrar.unregister(name);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
