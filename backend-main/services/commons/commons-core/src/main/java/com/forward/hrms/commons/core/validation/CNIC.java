package com.forward.hrms.commons.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pakistani CNIC in the standard hyphenated form: {@code XXXXX-XXXXXXX-X}.
 * Null is accepted (use {@code @NotNull} alongside if required).
 */
@Documented
@Constraint(validatedBy = CNICValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CNIC {
    String message() default "must be a valid CNIC (XXXXX-XXXXXXX-X)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
