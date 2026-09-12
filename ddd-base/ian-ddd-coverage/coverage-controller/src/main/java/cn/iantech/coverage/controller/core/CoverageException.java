package cn.iantech.coverage.controller.core;

/**
 * 覆盖率采集过程中的技术异常，用于把 TCP 通信/解析失败统一向上抛出。
 */
public class CoverageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CoverageException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoverageException(String message) {
        super(message);
    }
}
