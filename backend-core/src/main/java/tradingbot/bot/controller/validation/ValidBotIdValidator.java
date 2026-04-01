package tradingbot.bot.controller.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for ValidBotId annotation
 * Ensures bot IDs are valid numeric (Long) identifiers
 */
public class ValidBotIdValidator implements ConstraintValidator<ValidBotId, String> {

    @Override
    public void initialize(ValidBotId constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null values are handled by @NotNull annotation
        if (value == null || value.isEmpty()) {
            return true;
        }

        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
