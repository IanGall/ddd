package cn.iantech.gateway;

import cn.iantech.gateway.core.config.GatewayAuthFilter;
import cn.iantech.gateway.core.config.GatewayAuthFilter.Route;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 网关路由契约：{@link GatewayAuthFilter} 的路径白名单与控制器真实映射必须互相对得上。
 *
 * <p>白名单是**默认拒绝**模型里唯一的放行清单，因此它出错的方向比漏配更危险：
 * 白名单里出现拼错或已被删除的路径属于**过度放行**（fail-open）——该路径若日后被受保护端点复用，
 * 就会绕过鉴权，而单侧编译与测试都不会报错。故用 Spring MVC 的真实映射表交叉校验：</p>
 *
 * <ul>
 *   <li>白名单（{@code ANONYMOUS_ROUTES} / {@code PLATFORM_TOKEN_ROUTES}）的每个路径都必须有真实端点；</li>
 *   <li>控制器的每个映射都必须落在 {@code ROUTE_PREFIXES} 声明的三个分区内——否则过滤器会把它判为 DENIED，
 *       端点存在却永远 403。</li>
 * </ul>
 *
 * <p>反向的「漏把新端点加进白名单」不在本测试职责内：那种情况是 **fail-closed**（返回 401），
 * 开发者接入时立刻可见；方法维度（如白名单声明 POST 而端点是 GET）同样属于 fail-closed，且已由
 * {@code GatewayAuthFilterTest.shouldNotExposeAnonymousOrPlatformPathsWithWrongMethod} 覆盖。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "dubbo.registry.address=N/A",
                "dubbo.registry.username=test-user",
                "dubbo.registry.password=test-password",
                "dubbo.registry.use-as-metadata-center=false",
                "dubbo.config-center.address=N/A",
                "dubbo.application.metadata-type=local",
                "dubbo.application.metadata-service-protocol=injvm",
                "dubbo.consumer.init=false"
        })
class GatewayPublicPathContractTest {

    /** 只统计本服务的控制器，排除 Spring 自带的 /error 等框架端点。 */
    private static final String CONTROLLER_PACKAGE = "cn.iantech.gateway.controller";

    /** Actuator 的健康端点不由本仓库控制器提供，故不参与「必须有真实映射」的校验。 */
    private static final Set<String> EXCLUDED_FROM_MAPPING_CHECK =
            Set.of(GatewayAuthFilter.HEALTH_ROUTE.path());

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void whitelistedRoutesMustMapToRealEndpoints() {
        Set<String> mappedPaths = controllerPaths();

        List<String> stale = Stream.concat(GatewayAuthFilter.ANONYMOUS_ROUTES.stream(),
                        GatewayAuthFilter.PLATFORM_TOKEN_ROUTES.stream())
                .filter(route -> !EXCLUDED_FROM_MAPPING_CHECK.contains(route.path()))
                .filter(route -> !mappedPaths.contains(route.path()))
                .map(GatewayPublicPathContractTest::describe)
                .toList();

        Assertions.assertTrue(stale.isEmpty(),
                () -> "网关路由白名单包含不存在（或拼错）的路径，属过度放行风险：%s；当前控制器已映射路径：%s"
                        .formatted(stale, mappedPaths));
    }

    @Test
    void everyControllerEndpointMustLiveUnderDeclaredPrefixes() {
        List<String> outside = controllerPaths().stream()
                .filter(path -> GatewayAuthFilter.ROUTE_PREFIXES.stream().noneMatch(prefix -> matches(path, prefix)))
                .toList();

        Assertions.assertTrue(outside.isEmpty(),
                () -> "以下控制器端点不在 %s 三个分区内，过滤器会判为 DENIED（端点存在却恒 403）：%s"
                        .formatted(GatewayAuthFilter.ROUTE_PREFIXES, outside));
    }

    @Test
    void documentedRoutePrefixesMustMatchFilterConstants() throws IOException {
        Path readme = gatewayReadme();
        String content = Files.readString(readme, StandardCharsets.UTF_8);

        List<String> undocumented = GatewayAuthFilter.ROUTE_PREFIXES.stream()
                .filter(prefix -> !content.contains(prefix))
                .toList();

        Assertions.assertTrue(undocumented.isEmpty(),
                () -> "README 未写明这些 API 分区前缀（文件：%s）：%s".formatted(readme, undocumented));
    }

    private Set<String> controllerPaths() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith(CONTROLLER_PACKAGE))
                .flatMap(entry -> entry.getKey().getPatternValues().stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String describe(Route route) {
        return route.method() + " " + route.path();
    }

    private Path gatewayReadme() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return Stream.iterate(current, Objects::nonNull, Path::getParent)
                .map(path -> path.resolve("ian-ddd-gateway/README.md"))
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无法定位 ian-ddd-gateway/README.md: " + current));
    }
}
