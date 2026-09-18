package cn.iantech.gateway.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacWebRequestsValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void shouldLeavePasswordLengthToAuthServiceAndOnlyRejectBlank() {
        RbacWebRequests.CreateUser blank = new RbacWebRequests.CreateUser(
                "operator", "   ", null, null, null, true);
        RbacWebRequests.CreateUser tooLongForGateway = new RbacWebRequests.CreateUser(
                "operator", "a".repeat(73), null, null, null, true);

        // 网关只负责「非空」；8~72 个 UTF-8 字节的口径由 auth 服务的 PasswordPolicy 单一来源裁定。
        // 刻意不在网关用 @Size：Bean Validation 对 String 按字符计长，与服务端的字节口径会形成两套规则
        // （历史上导致 3 个中文这类合法口令在网关层被误拒）。
        assertFalse(VALIDATOR.validate(blank).isEmpty(), "空白密码应在网关层被拒");
        assertTrue(VALIDATOR.validate(tooLongForGateway).isEmpty(), "口令长度不再由网关裁定");
    }

    @Test
    void shouldLimitPermissionCodeToSixtyFourCharacters() {
        RbacWebRequests.CreatePermission valid = new RbacWebRequests.CreatePermission(
                "p".repeat(64), "查询权限", 1, null, null, null, true);
        RbacWebRequests.CreatePermission tooLong = new RbacWebRequests.CreatePermission(
                "p".repeat(65), "查询权限", 1, null, null, null, true);

        assertTrue(VALIDATOR.validate(valid).isEmpty());
        assertFalse(VALIDATOR.validate(tooLong).isEmpty());
    }
}
