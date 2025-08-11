package com.example.state_machine.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankWorkflowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bank Account Opening — State Machine API")
                        .description("State-machine driven onboarding with async voice biometrics and history audit")
                        .version("v1")
                        .contact(new Contact().name("Backend Team").email("backend@example.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("springdoc-openapi docs")
                        .url("https://springdoc.org"));
    }

    @Bean
    public GroupedOpenApi processApi() {
        return GroupedOpenApi.builder()
                .group("process")
                .pathsToMatch("/process/**")
                .build();
    }
}
