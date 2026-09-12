package cn.iantech.coverage.controller.web;

import cn.iantech.coverage.controller.core.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 提供 Session 的静态报告资源：HTML 报告、dashboard.html、overall.xml。
 */
@RestController
@RequestMapping("/api/coverage/sessions/{id}/reports")
public class ReportResourceController {

    private static final String REPORTS_SEGMENT = "/reports";

    private final SessionService sessionService;

    public ReportResourceController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 访问报告根路径时返回 index.html。
     */
    @GetMapping({"", "/"})
    public ResponseEntity<FileSystemResource> index(@PathVariable String id) {
        return serve(id, "index.html");
    }

    /**
     * 提供报告目录下的任意静态资源，路径直接取自请求 URI，避免通配路径变量在不同容器上的解析差异。
     */
    @GetMapping("/**")
    public ResponseEntity<FileSystemResource> resource(@PathVariable String id, HttpServletRequest request) {
        String uri = request.getRequestURI();
        int index = uri.indexOf(REPORTS_SEGMENT);
        String path = index < 0 ? "" : uri.substring(index + REPORTS_SEGMENT.length());
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty() || path.endsWith("/")) {
            path = path + "index.html";
        }
        return serve(id, path);
    }

    private ResponseEntity<FileSystemResource> serve(String id, String relativePath) {
        Path sessionDirectory = sessionService.workDirectory().resolve("sessions").resolve(id).normalize();
        Path base = sessionDirectory.resolve("reports").normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base) || !Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "报告文件不存在: " + relativePath);
        }
        return ResponseEntity.ok().contentType(mediaType(target)).body(new FileSystemResource(target));
    }

    private MediaType mediaType(Path target) {
        String name = target.getFileName().toString();
        if (name.endsWith(".html")) {
            return MediaType.TEXT_HTML;
        }
        if (name.endsWith(".xml")) {
            return MediaType.APPLICATION_XML;
        }
        if (name.endsWith(".css")) {
            return new MediaType("text", "css");
        }
        if (name.endsWith(".js")) {
            return new MediaType("text", "javascript");
        }
        if (name.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (name.endsWith(".svg")) {
            return new MediaType("image", "svg+xml");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
