package cn.iantech.domain.auth.service;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 口令长度口径：按 UTF-8 字节计长、拒绝纯空白、非空白不做 trim。
 *
 * <p>边界用例覆盖管理端与 C 端曾出现分歧的场景（中文按字节而非字符计长）。</p>
 */
class PasswordPolicyTest {

    @Test
    void shouldCountUtf8BytesRatherThanCharacters() {
        // 3 个中文 = 9 字节，达到 8 字节下限
        assertDoesNotThrow(() -> PasswordPolicy.check("密码啊"));
        // 2 个中文 = 6 字节，低于下限
        assertRejected("密码");
    }

    @Test
    void shouldEnforceMinimumOfEightBytes() {
        assertRejected("1234567");
        assertDoesNotThrow(() -> PasswordPolicy.check("12345678"));
    }

    @Test
    void shouldEnforceMaximumOfSeventyTwoBytes() {
        assertDoesNotThrow(() -> PasswordPolicy.check("a".repeat(72)));
        assertRejected("a".repeat(73));
        // 24 个中文 = 72 字节（上限内）；25 个中文 = 75 字节（超限）
        assertDoesNotThrow(() -> PasswordPolicy.check("码".repeat(24)));
        assertRejected("码".repeat(25));
    }

    @Test
    void shouldRejectBlankInput() {
        assertRejected(null);
        assertRejected("");
        assertRejected(" ".repeat(8));
    }

    @Test
    void shouldNotTrimNonBlankPassword() {
        // 若实现做了 trim，该口令会变成 6 字节而被拒；按原样计长为 8 字节，应通过
        assertDoesNotThrow(() -> PasswordPolicy.check(" 123456 "));
    }

    @Test
    void shouldReportInvalidArgumentCode() {
        AppException exception = assertThrows(AppException.class, () -> PasswordPolicy.check("短"));
        assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), exception.getCode());
    }

    private static void assertRejected(String password) {
        AppException exception = assertThrows(AppException.class, () -> PasswordPolicy.check(password),
                "非法口令必须被拒绝：" + password);
        assertEquals(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), exception.getCode());
    }
}
