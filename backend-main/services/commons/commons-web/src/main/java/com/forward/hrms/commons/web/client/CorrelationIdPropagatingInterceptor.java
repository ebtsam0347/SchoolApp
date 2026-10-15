package com.forward.hrms.commons.web.client;

import com.forward.hrms.commons.core.context.CorrelationId;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Stamps {@code X-Correlation-Id} on every outbound request from
 * {@link CorrelationId#get() the current thread-local}. The downstream
 * service's {@code CorrelationIdFilter} reads it back on the way in,
 * stitching the request log on both sides under the same id.
 *
 * <p>If no correlation id is present on the calling thread (e.g. a
 * scheduler-thread call that did not seed one) the header is omitted and
 * downstream generates a fresh id.
 */
public final class CorrelationIdPropagatingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        CorrelationId.get().ifPresent(id -> request.getHeaders().set(CorrelationId.HEADER, id));
        return execution.execute(request, body);
    }
}
