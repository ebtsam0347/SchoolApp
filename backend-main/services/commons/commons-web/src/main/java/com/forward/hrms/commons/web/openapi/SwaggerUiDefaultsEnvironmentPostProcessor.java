package com.forward.hrms.commons.web.openapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Springdoc / swagger-ui defaults shared by every FSHRMS service.
 *
 * Registered with the lowest precedence (`addLast`) so any service can
 * override individual keys via its own `application.yml`.
 *
 * If you tweak a default here, also update `docs/common-lib.md`
 * ("OpenAPI / Swagger UI") so frontend devs know what to expect.
 */
public class SwaggerUiDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "fshrms-swagger-ui-defaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (env.getPropertySources().contains(SOURCE_NAME)) return;
        Map<String, Object> d = new LinkedHashMap<>();

        // --- Paths ---
        d.put("springdoc.api-docs.path", "/v3/api-docs");
        d.put("springdoc.swagger-ui.path", "/swagger-ui.html");

        // --- Try-it-out / interaction (the whole point of the UI here) ---
        d.put("springdoc.swagger-ui.try-it-out-enabled", true);
        d.put("springdoc.swagger-ui.persist-authorization", true);
        d.put("springdoc.swagger-ui.deep-linking", true);
        d.put("springdoc.swagger-ui.request-snippets-enabled", true);
        d.put("springdoc.swagger-ui.display-request-duration", true);

        // --- Listing / presentation ---
        d.put("springdoc.swagger-ui.filter", true);
        d.put("springdoc.swagger-ui.tags-sorter", "alpha");
        d.put("springdoc.swagger-ui.operations-sorter", "alpha");
        d.put("springdoc.swagger-ui.doc-expansion", "none");
        d.put("springdoc.swagger-ui.disable-swagger-default-url", true);

        // --- Schema / model rendering (FE-dev friendly) ---
        // Show example payloads first, not the abstract schema.
        d.put("springdoc.swagger-ui.default-model-rendering", "example");
        // Surface validation hints (minLength, pattern, required, …) inline.
        d.put("springdoc.swagger-ui.show-common-extensions", true);
        d.put("springdoc.swagger-ui.show-extensions", true);
        d.put("springdoc.swagger-ui.default-models-expand-depth", 1);
        d.put("springdoc.swagger-ui.default-model-expand-depth", 2);
        d.put("springdoc.swagger-ui.display-operation-id", false);
        // Disable Swagger.io online validator badge — privacy + works offline.
        d.put("springdoc.swagger-ui.validator-url", "");
        d.put("springdoc.swagger-ui.syntax-highlight.activated", true);

        // --- Spec generation ---
        d.put("springdoc.show-actuator", false);
        d.put("springdoc.writer-with-default-pretty-printer", true);
        // Flatten Spring's `Pageable` into page/size/sort query params instead
        // of a nested object schema — paginated list endpoints render cleanly.
        d.put("springdoc.model-converters.pageable-converter.enabled", true);
        d.put("springdoc.model-converters.sort-converter.enabled", true);
        // Default request/response media type — every service is JSON-only.
        d.put("springdoc.default-consumes-media-type", "application/json");
        d.put("springdoc.default-produces-media-type", "application/json");

        env.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, d));
    }
}
