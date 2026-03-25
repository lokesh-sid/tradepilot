package tradingbot.bot.controller.validation;

import java.util.UUID;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for ValidBotId annotation
 * Ensures bot IDs are valid UUIDs
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
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
