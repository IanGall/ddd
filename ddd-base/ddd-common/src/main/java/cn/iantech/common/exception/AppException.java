package cn.iantech.common.exception;

public class AppException extends RuntimeException {

    private static final long serialVersionUID = 5317680961212299217L;

    /** 异常码 */
    private final String code;

    /** 异常信息 */
    private final String info;

    public AppException(String code) {
        this(code, (String) null, null);
    }

    public AppException(String code, Throwable cause) {
        this(code, null, cause);
    }

    public AppException(String code, String message) {
        this(code, message, null);
    }

    public AppException(String code, String message, Throwable cause) {
        super(message == null ? code : message, cause);
        this.code = code;
        this.info = message;
    }

    public String getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }

}
