package com.forward.hrms.commons.web.client;

import com.forward.hrms.commons.web.security.IdentityHeaders;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Optional;

/**
 * Copies the {@code X-User-*} headers from the inbound request onto every
 * outbound downstream call. Downstream services run in header-identity mode
 * and trust those headers because they come from inside the gateway-fronted
 * trust boundary.
 *
 * <p>If the inbound request didn't carry an identity (e.g. an unauthenticated
 * actuator probe or a scheduler-thread call), nothing is forwarded and the
 * downstream service rejects with 401 if its own chain requires
 * authentication.
 *
 * <p>Auto-registered on every {@code RestClient.Builder} via the
 * {@link com.forward.hrms.commons.web.autoconfig.HardeningAutoConfiguration
 * commons-web RestClientCustomizer} — services do not wire this manually.
 */
public final class IdentityForwardingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        currentRequest().ifPresent(req -> {
            HttpHeaders headers = request.getHeaders();
            for (String name : IdentityHeaders.ALL) {
                String value = req.getHeader(name);
                if (value != null && !value.isBlank()) headers.set(name, value);
            }
        });
        return execution.execute(request, body);
    }

    private static Optional<HttpServletRequest> currentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest);
    }
}
