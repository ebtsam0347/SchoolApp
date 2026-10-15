package com.forward.hrms.employee.enums.travel;

import com.forward.hrms.commons.core.error.UnprocessableError;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Business travel types, and the single source of truth for the
 * {@code travelTypeCode -> policyCode} mapping used when a Travel
 * Request resolves its applicable {@code TravelPolicyVersion}.
 *
 * <p>Each travel type maps to exactly one travel policy family:
 *
 * <pre>
 * LOCAL         -&gt; LOCAL_FACTORY_TRAVEL
 * INTERNATIONAL -&gt; INTERNATIONAL_FACTORY_TRAVEL
 * </pre>
 *
 * <p>A policy family evolves through numbered versions (v1, v2, ...)
 * of which at most one may be ACTIVE at a time; the version applicable
 * to a trip is the ACTIVE one whose effective window covers the trip's
 * start date.
 */
public enum TravelType {

    /** Domestic travel. Resolves against {@code LOCAL_FACTORY_TRAVEL}. */
    LOCAL("LOCAL_FACTORY_TRAVEL"),

    /** International travel. Resolves against {@code INTERNATIONAL_FACTORY_TRAVEL}. */
    INTERNATIONAL("INTERNATIONAL_FACTORY_TRAVEL");

    private final String policyCode;

    TravelType(String policyCode) {
        this.policyCode = policyCode;
    }

    /** Travel policy family this travel type resolves its policy version from. */
    public String policyCode() {
        return policyCode;
    }

    /**
     * Parses a client-supplied {@code travelTypeCode}. An unknown or blank
     * code is rejected rather than silently resolving an arbitrary policy.
     *
     * @throws UnprocessableError with {@link TravelErrorCode#TRAVEL_INVALID_TRAVEL_TYPE}
     */
    public static TravelType parse(String code) {
        if (code == null || code.isBlank()) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_INVALID_TRAVEL_TYPE,
                    "Travel type code is required");
        }
        try {
            return TravelType.valueOf(code.trim());
        } catch (IllegalArgumentException ex) {
            throw new UnprocessableError(TravelErrorCode.TRAVEL_INVALID_TRAVEL_TYPE,
                    "Unknown travel type code: " + code);
        }
    }

    /**
     * Guards a client-supplied {@code policyCode} when a policy version is
     * created. The supported families are exactly the ones this enum maps
     * to, so an unsupported code is rejected rather than creating a policy
     * family no Travel Request could ever resolve — the failure would
     * otherwise surface far from its cause, as
     * {@code TRAVEL_NO_APPLICABLE_POLICY} at trip-creation time.
     *
     * <p>Matched exactly, with no trimming or case folding: a code that
     * needed normalizing to match would be stored in its raw form and then
     * never resolve.
     *
     * @throws UnprocessableError with {@link TravelErrorCode#TRAVEL_POLICY_CODE_UNSUPPORTED}
     */
    public static void requireSupportedPolicyCode(String policyCode) {
        for (TravelType t : values()) {
            if (t.policyCode.equals(policyCode)) {
                return;
            }
        }
        throw new UnprocessableError(TravelErrorCode.TRAVEL_POLICY_CODE_UNSUPPORTED,
                "Unsupported travel policy code: " + policyCode
                        + "; supported codes are " + supportedPolicyCodes());
    }

    /** The supported policy codes, for error messages. */
    private static String supportedPolicyCodes() {
        return Arrays.stream(values()).map(TravelType::policyCode).collect(Collectors.joining(", "));
    }
}
