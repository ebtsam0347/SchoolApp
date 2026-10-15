package com.forward.hrms.commons.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PakistaniMobileValidator implements ConstraintValidator<PakistaniMobile, String> {

    private static final Pattern PATTERN = Pattern.compile("^(\\+923|03)\\d{9}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || PATTERN.matcher(value).matches();
    }
}
