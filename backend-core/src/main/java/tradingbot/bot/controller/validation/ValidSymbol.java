package tradingbot.bot.controller.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that trading symbol follows correct format (e.g., BTCUSDT)
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidSymbolValidator.class)
@Documented
public @interface ValidSymbol {
    
    String message() default "Invalid trading symbol format. Must be uppercase alphanumeric (e.g., BTCUSDT)";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
