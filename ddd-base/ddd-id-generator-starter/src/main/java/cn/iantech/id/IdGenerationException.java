package cn.iantech.id;

/**
 * ID 生成失败异常。
 */
public final class IdGenerationException extends RuntimeException {

    public IdGenerationException(String message) {
        super(message);
    }

    public IdGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
