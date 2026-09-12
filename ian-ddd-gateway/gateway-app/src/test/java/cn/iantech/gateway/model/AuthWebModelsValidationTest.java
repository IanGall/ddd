package cn.iantech.gateway.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthWebModelsValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void shouldAcceptV4RefreshTokenAndLeaveStrictParsingToAuth() {
        String token = "v4.1001.abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

        assertTrue(VALIDATOR.validate(new AuthWebModels.RefreshRequest(token, "web", "device")).isEmpty());
        assertFalse(VALIDATOR.validate(new AuthWebModels.RefreshRequest("", "web", "device")).isEmpty());
        assertFalse(VALIDATOR.validate(new AuthWebModels.RefreshRequest("x".repeat(257), "web", "device")).isEmpty());
    }
}
