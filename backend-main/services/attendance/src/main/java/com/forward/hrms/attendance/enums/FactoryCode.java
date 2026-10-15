package com.forward.hrms.attendance.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Factory / unit an overtime request is worked for, picked by the applicant
 * when filing. {@link #code()} is the literal value on the wire and in
 * {@code overtime_requests.factory_code} — note {@code Administrator} is
 * mixed-case, which is why this isn't stored by {@link #name()}.
 */
public enum FactoryCode {
    B34002("B34002"),
    B34003("B34003"),
    B34004("B34004"),
    B34005("B34005"),
    B34006("B34006"),
    B34007("B34007"),
    ADMINISTRATOR("Administrator");

    private final String code;

    FactoryCode(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** Exact code match; null for null. Unknown codes are a 400 via Jackson. */
    @JsonCreator
    public static FactoryCode fromCode(String code) {
        if (code == null) return null;
        for (FactoryCode f : values()) {
            if (f.code.equals(code)) return f;
        }
        throw new IllegalArgumentException("unknown factory code: " + code);
    }
}
