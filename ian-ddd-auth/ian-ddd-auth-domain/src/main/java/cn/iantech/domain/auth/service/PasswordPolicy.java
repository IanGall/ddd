package cn.iantech.domain.auth.service;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 口令长度策略的唯一来源，由管理端（RBAC）与 C 端注册共同复用。
 *
 * <p><b>口径</b>：按 UTF-8 字节计长，下限 8 字节、上限 72 字节。72 字节上限与 BCrypt
 * 新密码编码的限制一致（超出该长度的口令在编码阶段会被直接拒绝）；<b>8 字节下限是本项目的
 * 业务策略，并非 BCrypt 的要求</b>。</p>
 *
 * <p><b>刻意不做 trim</b>：只拒绝纯空白口令；含首尾空格的口令按原样计长与编码，避免
 * "用户以为设置的是 A、实际生效的是 B"。因此本类不适用于登录校验路径（比对的是既有哈希）。</p>
 *
 * <p>只承载纯函数：不访问仓储、不持有状态，可被 cases 与 domain 安全复用。</p>
 */
public final class PasswordPolicy {

    /** 下限为业务策略（8 字节约合 3 个中文字符）。 */
    public static final int MIN_PASSWORD_BYTES = 8;

    /** 上限与 BCrypt 新密码编码的限制一致。 */
    public static final int MAX_PASSWORD_BYTES = 72;

    private static final String LENGTH_MESSAGE = "密码长度必须为 8 至 72 个 UTF-8 字节";

    private PasswordPolicy() {
    }

    /**
     * 校验口令长度，非法时抛出 {@code INVALID_ARGUMENT} 业务异常。
     *
     * @param rawPassword 原始口令；{@code null}、空串与纯空白均视为非法
     */
    public static void check(String rawPassword) {
        if (StringUtils.isBlank(rawPassword)) {
            throw illegalArgument();
        }
        int passwordBytes = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes < MIN_PASSWORD_BYTES || passwordBytes > MAX_PASSWORD_BYTES) {
            throw illegalArgument();
        }
    }

    private static AppException illegalArgument() {
        return new AppException(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), LENGTH_MESSAGE);
    }
}
