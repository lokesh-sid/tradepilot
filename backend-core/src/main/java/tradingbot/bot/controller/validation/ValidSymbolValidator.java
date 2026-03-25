package tradingbot.bot.controller.validation;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for ValidSymbol annotation
 * Ensures trading symbols follow correct format
 */
public class ValidSymbolValidator implements ConstraintValidator<ValidSymbol, String> {
    
    // Pattern for valid trading symbols (e.g., BTCUSDT, ETHUSDT)
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9]{4,20}$");
    
    @Override
    public void initialize(ValidSymbol constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null values are handled by @NotNull annotation
        if (value == null || value.isEmpty()) {
            return true;
        }
        
        return SYMBOL_PATTERN.matcher(value).matches();
    }
}
