package cn.iantech.coverage.controller.web;

import cn.iantech.coverage.controller.core.CoverageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 覆盖率控制 API 的统一异常响应：把技术异常转换成可读的 HTTP 状态与说明。
 */
@RestControllerAdvice
public class CoverageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CoverageExceptionHandler.class);

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(CoverageException.class)
    public ProblemDetail handleCoverageFailure(CoverageException e) {
        log.error("覆盖率采集失败", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setDetail(detail);
        return problem;
    }
}
