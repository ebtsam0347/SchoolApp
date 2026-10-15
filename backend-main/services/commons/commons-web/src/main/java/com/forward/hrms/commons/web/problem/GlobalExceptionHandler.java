package com.forward.hrms.commons.web.problem;

import com.forward.hrms.commons.core.error.BusinessError;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.RateLimitedError;
import com.forward.hrms.commons.core.error.Violation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Single source of error rendering — wires every exception we care about
 * into the extended ProblemDetail shape from docs/api-conventions.md.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessError.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessError e, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetailBuilder.of(e, req.getRequestURI());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (e instanceof RateLimitedError rl && rl.retryAfter() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(rl.retryAfter().toSeconds()));
        }
        return new ResponseEntity<>(pd, headers, HttpStatus.valueOf(e.status()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<Violation> violations = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new Violation(
                        fe.getField(),
                        fe.getCode() == null ? "invalid" : fe.getCode(),
                        fe.getDefaultMessage()))
                .toList();
        ProblemDetail pd = ProblemDetailBuilder.validation("Request body has invalid fields.", violations, req.getRequestURI());
        return problem(pd, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException e, HttpServletRequest req) {
        List<Violation> violations = e.getConstraintViolations().stream()
                .map(cv -> new Violation(
                        cv.getPropertyPath().toString(),
                        cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        cv.getMessage()))
                .toList();
        ProblemDetail pd = ProblemDetailBuilder.validation("Request parameters are invalid.", violations, req.getRequestURI());
        return problem(pd, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleMalformed(Exception e, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetailBuilder.of(HttpStatus.BAD_REQUEST,
                CommonErrorCode.VALIDATION_FAILED,
                "Malformed request: " + e.getMessage(),
                req.getRequestURI());
        return problem(pd, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> handleNoHandler(Exception e, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetailBuilder.of(HttpStatus.NOT_FOUND,
                CommonErrorCode.NOT_FOUND, "No endpoint at " + req.getRequestURI(), req.getRequestURI());
        return problem(pd, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuth(AuthenticationException e, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetailBuilder.of(HttpStatus.UNAUTHORIZED,
                CommonErrorCode.UNAUTHENTICATED, "Authentication required.", req.getRequestURI());
        return problem(pd, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleDenied(AccessDeniedException e, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetailBuilder.of(HttpStatus.FORBIDDEN,
                CommonErrorCode.FORBIDDEN, "You don't have permission for this resource.", req.getRequestURI());
        return problem(pd, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException e, HttpServletRequest req) {
        // Client closed the connection mid-response (typical for SSE streams like
        // /bff/notifications/stream when the browser tab closes or reconnects).
        // Response is already gone — don't try to render a body.
        log.debug("Client disconnected serving {} {}: {}", req.getMethod(), req.getRequestURI(), e.getMessage());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetail> handleAny(Throwable e, HttpServletRequest req) {
        log.error("Unhandled exception serving {} {}", req.getMethod(), req.getRequestURI(), e);
        ProblemDetail pd = ProblemDetailBuilder.of(HttpStatus.INTERNAL_SERVER_ERROR,
                CommonErrorCode.INTERNAL_ERROR,
                "An internal error occurred. Quote the correlationId when reporting.",
                req.getRequestURI());
        return problem(pd, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<ProblemDetail> problem(ProblemDetail pd, HttpStatus status) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, h, status);
    }
}
