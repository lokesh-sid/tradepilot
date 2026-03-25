package tradingbot.bot.controller.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for ValidLeverage annotation
 * Ensures leverage values are within acceptable range
 */
public class ValidLeverageValidator implements ConstraintValidator<ValidLeverage, Number> {
    
    private int min;
    private int max;
    
    @Override
    public void initialize(ValidLeverage constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
    }
    
    @Override
    public boolean isValid(Number value, ConstraintValidatorContext context) {
        // Null values are handled by @NotNull annotation
        if (value == null) {
            return true;
        }
        
        double leverage = value.doubleValue();
        
        if (leverage < min || leverage > max) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Leverage must be between %d and %d", min, max)
            ).addConstraintViolation();
            return false;
        }
        
        return true;
    }
}
