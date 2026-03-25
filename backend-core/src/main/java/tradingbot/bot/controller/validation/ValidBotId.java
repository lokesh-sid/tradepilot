package tradingbot.bot.controller.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that a bot ID is in valid UUID format
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidBotIdValidator.class)
@Documented
public @interface ValidBotId {
    
    String message() default "Invalid bot ID format. Must be a valid UUID";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
