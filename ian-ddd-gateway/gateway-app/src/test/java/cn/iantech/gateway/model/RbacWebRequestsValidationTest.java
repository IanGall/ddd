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
    void shouldLimitUserPasswordToEightThroughSeventyTwoCharacters() {
        RbacWebRequests.CreateUser valid = new RbacWebRequests.CreateUser(
                "operator", "a".repeat(72), null, null, null, true);
        RbacWebRequests.CreateUser tooLong = new RbacWebRequests.CreateUser(
                "operator", "a".repeat(73), null, null, null, true);

        assertTrue(VALIDATOR.validate(valid).isEmpty());
        assertFalse(VALIDATOR.validate(tooLong).isEmpty());
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
