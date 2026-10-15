package com.forward.hrms.auth.service;

import java.util.ArrayList;
import java.util.List;

import com.forward.hrms.auth.enums.AuthErrorCode;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    private static final java.util.Set<String> BANNED_SUBSTRINGS = java.util.Set.of(
            "password", "changeme", "qwerty", "12345678", "letmein",
            "welcome", "admin", "forwardsports", "fshrms");

    private PasswordPolicy() {}

    /** Backwards-compatible call site (no username available). */
    public static void validate(String password) {
        validate(password, null);
    }

    /**
     * @param username when non-null, rejects passwords that contain the
     *                 username as a substring (case-insensitive).
     */
    public static void validate(String password, String username) {
        List<Violation> v = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            v.add(new Violation("newPassword", AuthErrorCode.PASSWORD_TOO_SHORT.name(),
                    "password must be at least " + MIN_LENGTH + " characters"));
            throw new ValidationError("password does not meet policy", v);
        }
        boolean upper = false, lower = false, digit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
        }
        if (!upper || !lower || !digit) {
            v.add(new Violation("newPassword", AuthErrorCode.PASSWORD_TOO_SHORT.name(),
                    "password must contain at least one uppercase, one lowercase, and one digit"));
        }
        String lc = password.toLowerCase(java.util.Locale.ROOT);
        for (String banned : BANNED_SUBSTRINGS) {
            if (lc.contains(banned)) {
                v.add(new Violation("newPassword", AuthErrorCode.PASSWORD_TOO_SHORT.name(),
                        "password contains a forbidden term"));
                break;
            }
        }
        if (username != null && !username.isBlank()
                && lc.contains(username.toLowerCase(java.util.Locale.ROOT))) {
            v.add(new Violation("newPassword", AuthErrorCode.PASSWORD_TOO_SHORT.name(),
                    "password must not contain the username"));
        }
        if (!v.isEmpty()) throw new ValidationError("password does not meet policy", v);
    }
}
