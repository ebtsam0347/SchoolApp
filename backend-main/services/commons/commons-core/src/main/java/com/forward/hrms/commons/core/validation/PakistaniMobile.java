package com.forward.hrms.commons.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pakistani mobile number — accepts {@code +923XXXXXXXXX} or
 * {@code 03XXXXXXXXX}. Null is accepted (combine with {@code @NotNull}
 * when the field is required).
 */
@Documented
@Constraint(validatedBy = PakistaniMobileValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PakistaniMobile {
    String message() default "must be a valid Pakistani mobile number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
