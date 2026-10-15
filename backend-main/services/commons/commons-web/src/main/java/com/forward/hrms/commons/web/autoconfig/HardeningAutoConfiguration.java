package com.forward.hrms.commons.web.autoconfig;

import com.forward.hrms.commons.web.client.CorrelationIdPropagatingInterceptor;
import com.forward.hrms.commons.web.client.IdentityForwardingInterceptor;
import com.forward.hrms.commons.web.client.WebClientProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Phase 5 hardening: every {@code RestClient.Builder} injection point gets
 * the standard outbound interceptors and outbound timeouts pre-registered.
 *
 * <ul>
 *   <li>{@link IdentityForwardingInterceptor} copies {@code X-User-*} from
 *       the inbound request to the outbound call (in-trust-boundary
 *       service-to-service identity propagation).
 *   <li>{@link CorrelationIdPropagatingInterceptor} stamps
 *       {@code X-Correlation-Id} from the current thread.
 *   <li>Connect/read timeouts default to 5 s / 10 s and override via
 *       {@code hrms.web.client.{connect,read}-timeout}. A hung backend
 *       no longer hangs the request thread forever.
 * </ul>
 *
 * <p>OTEL tracing context (W3C traceparent) is propagated automatically by
 * {@code micrometer-tracing-bridge-otel} once it's on the classpath — no
 * manual interceptor needed for spans.
 *
 * <p>The {@code @ConditionalOnClass(RestClient.class)} guard keeps this
 * autoconfig dormant if a future module excludes the RestClient stack;
 * harmless under normal commons-web usage.
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(WebClientProperties.class)
public class HardeningAutoConfiguration {

    @Bean
    public RestClientCustomizer hrmsOutboundInterceptors() {
        IdentityForwardingInterceptor identity = new IdentityForwardingInterceptor();
        CorrelationIdPropagatingInterceptor correlation = new CorrelationIdPropagatingInterceptor();
        return builder -> builder.requestInterceptors(list -> {
            list.add(correlation);
            list.add(identity);
        });
    }

    /**
     * Separate customizer (Spring runs every {@code RestClientCustomizer}
     * in turn) so per-service customizers that add their own factory
     * still get the timeouts unless they override them explicitly.
     * {@link JdkClientHttpRequestFactory} wraps the JDK 21
     * {@code java.net.http.HttpClient} which honours both timeouts.
     */
    @Bean
    public RestClientCustomizer hrmsOutboundTimeouts(WebClientProperties props) {
        return builder -> {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
            factory.setReadTimeout(props.readTimeout());
            // Connect timeout for JdkClientHttpRequestFactory comes from
            // the underlying HttpClient; build one ourselves and pass it in.
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(props.connectTimeout())
                    .build();
            JdkClientHttpRequestFactory tuned = new JdkClientHttpRequestFactory(client);
            tuned.setReadTimeout(props.readTimeout());
            builder.requestFactory(tuned);
        };
    }
}
