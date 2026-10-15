package com.forward.hrms.commons.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class CNICValidator implements ConstraintValidator<CNIC, String> {

    private static final Pattern PATTERN = Pattern.compile("^\\d{5}-\\d{7}-\\d$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || PATTERN.matcher(value).matches();
    }
}
