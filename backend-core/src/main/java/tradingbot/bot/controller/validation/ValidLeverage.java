package tradingbot.bot.controller.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that leverage is within acceptable range (1-125x for most exchanges)
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidLeverageValidator.class)
@Documented
public @interface ValidLeverage {
    
    String message() default "Leverage must be between {min} and {max}";
    
    int min() default 1;
    
    int max() default 125;
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
