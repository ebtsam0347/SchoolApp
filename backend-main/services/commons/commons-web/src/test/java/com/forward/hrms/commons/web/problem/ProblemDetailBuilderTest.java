package com.forward.hrms.commons.web.problem;

import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the FE-facing error contract — every 4xx must carry the standard
 * extensions (code, correlationId, timestamp) plus violations[] for
 * validation errors. If this drifts, every FE error handler silently
 * breaks.
 */
class ProblemDetailBuilderTest {

    @Test
    void businessError_carriesCodeCorrelationIdTimestamp() {
        var err = new ConflictError(CommonErrorCode.CONFLICT, "duplicate key");
        ProblemDetail pd = ProblemDetailBuilder.of(err, "/api/things/42");

        assertEquals(409, pd.getStatus());
        assertEquals("duplicate key", pd.getDetail());
        assertEquals("/api/things/42", pd.getInstance().toString());

        Map<String, Object> ext = pd.getProperties();
        assertNotNull(ext);
        assertEquals("CONFLICT", ext.get("code"));
        assertNotNull(ext.get("timestamp"));
        // correlationId is null when no correlation context is set; the key
        // must still be present so FE can read it uniformly.
        assertTrue(ext.containsKey("correlationId"));

        // Type URI follows the doctrine: forwardsports.com/errors/<kebab-code>
        assertEquals("https://forwardsports.com/errors/conflict", pd.getType().toString());
        assertEquals("Conflict", pd.getTitle());
    }

    @Test
    void validationError_attachesViolations() {
        List<Violation> violations = List.of(
                new Violation("email", "NotBlank", "must not be blank"),
                new Violation("age", "Min", "must be >= 18"));
        var err = new ValidationError("invalid request", violations);
        ProblemDetail pd = ProblemDetailBuilder.of(err, "/api/users");

        assertEquals(400, pd.getStatus());
        Object out = pd.getProperties().get("violations");
        assertTrue(out instanceof List);
        assertEquals(2, ((List<?>) out).size());
        assertEquals("VALIDATION_FAILED", pd.getProperties().get("code"));
    }

    @Test
    void plainStatusBuilder_carriesSameExtensions() {
        ProblemDetail pd = ProblemDetailBuilder.of(
                org.springframework.http.HttpStatus.NOT_FOUND,
                CommonErrorCode.NOT_FOUND,
                "no employee 42",
                "/employees/42");

        assertEquals(404, pd.getStatus());
        assertEquals("NOT_FOUND", pd.getProperties().get("code"));
        assertNotNull(pd.getProperties().get("timestamp"));
        assertTrue(pd.getProperties().containsKey("correlationId"));
        assertEquals("https://forwardsports.com/errors/not-found", pd.getType().toString());
    }

    @Test
    void businessError_extensionsPropagateToProblemDetail() {
        var err = new ConflictError(CommonErrorCode.CONFLICT, "version mismatch",
                Map.of("expectedVersion", 5, "actualVersion", 7));
        ProblemDetail pd = ProblemDetailBuilder.of(err, "/things/1");

        assertEquals(5, pd.getProperties().get("expectedVersion"));
        assertEquals(7, pd.getProperties().get("actualVersion"));
    }
}
