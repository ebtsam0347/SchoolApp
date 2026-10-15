package com.forward.hrms.commons.web.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(prefix = "hrms.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI hrmsOpenAPI(OpenApiProperties props,
                               @Value("${spring.application.name:service}") String appName) {

        String title = (props.title() == null || props.title().isBlank())
                ? "Spirit of Islam School HRMS — " + appName
                : props.title();
        String description = (props.description() == null || props.description().isBlank())
                ? "The Spirit of Islam School HRMS — " + appName + " service API."
                : props.description();

        Info info = new Info()
                .title(title)
                .description(description)
                .version(props.version())
                .license(new License().name(props.licenseName()).url(props.licenseUrl()));

        if ((props.contactName() != null && !props.contactName().isBlank())
                || (props.contactEmail() != null && !props.contactEmail().isBlank())) {
            info.contact(new Contact().name(props.contactName()).email(props.contactEmail()));
        }

        OpenAPI api = new OpenAPI().info(info);

        if (props.serverUrl() != null && !props.serverUrl().isBlank()) {
            api.addServersItem(new Server().url(props.serverUrl()).description(props.serverDescription()));
        }

        if (props.security() != null && props.security().enabled()) {
            String name = props.security().name();
            api.components(new Components().addSecuritySchemes(name,
                    new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme(props.security().scheme())
                            .bearerFormat(props.security().bearerFormat())
                            .in(SecurityScheme.In.HEADER)
                            .name(name)
                            .description("Paste the access token returned from POST /auth/login (without the 'Bearer ' prefix).")));
            api.addSecurityItem(new SecurityRequirement().addList(name));
        }

        return api;
    }
}
