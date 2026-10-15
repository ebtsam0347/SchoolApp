package com.forward.hrms.commons.web.problem;

import com.forward.hrms.commons.core.context.CorrelationId;
import com.forward.hrms.commons.core.error.BusinessError;
import com.forward.hrms.commons.core.error.ErrorCode;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link ProblemDetail} matching docs/api-conventions.md §3 — adds
 * the standard extensions ({@code code}, {@code correlationId},
 * {@code timestamp}, optional {@code violations}, plus per-error
 * extensions) so handlers don't duplicate the wiring.
 */
public final class ProblemDetailBuilder {

    private static final String TYPE_BASE = "https://forwardsports.com/errors/";

    private ProblemDetailBuilder() {}

    public static ProblemDetail of(BusinessError e, String instance) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.status()), e.getMessage());
        decorate(pd, e.code(), instance);
        e.extensions().forEach(pd::setProperty);
        if (e instanceof ValidationError ve && !ve.violations().isEmpty()) {
            pd.setProperty("violations", ve.violations());
        }
        return pd;
    }

    public static ProblemDetail of(HttpStatus status, ErrorCode code, String detail, String instance) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        decorate(pd, code, instance);
        return pd;
    }

    public static ProblemDetail validation(String detail, List<Violation> violations, String instance) {
        ValidationError ve = new ValidationError(detail, violations);
        return of(ve, instance);
    }

    private static void decorate(ProblemDetail pd, ErrorCode code, String instance) {
        pd.setType(URI.create(TYPE_BASE + code.name().toLowerCase().replace('_', '-')));
        pd.setTitle(humanize(code));
        if (instance != null) pd.setInstance(URI.create(instance));
        pd.setProperty("code", code.name());
        pd.setProperty("correlationId", CorrelationId.get().orElse(null));
        pd.setProperty("timestamp", Instant.now().toString());
    }

    private static String humanize(ErrorCode code) {
        String[] parts = code.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (i == 0) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            else sb.append(' ').append(w);
        }
        return sb.toString();
    }

    public static Map<String, Object> noExtensions() { return Map.of(); }
}
