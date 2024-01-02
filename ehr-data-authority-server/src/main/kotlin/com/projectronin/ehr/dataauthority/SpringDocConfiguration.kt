package com.projectronin.ehr.dataauthority

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import jakarta.annotation.Generated
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// This was originally generated, but copied here to prevent some issues with the generator requiring Spring Boot 3
@Generated(value = ["org.openapitools.codegen.languages.KotlinSpringServerCodegen"])
@Configuration
class SpringDocConfiguration {
    @Bean
    fun apiInfo(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("EHR Data Authority")
                    .description(
                        "The EHR Data Authority enforces RCDM. " +
                            "Responsible for data insertion and providing downstream events of new and changing data.",
                    )
                    .version("1.0.0"),
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        "auth0",
                        // This is defined as oauth2 within the OpenAPI spec to match the actual use,
                        // but is defined as Bearer here to allow for "Try It Out" support that is not available with Swagger and Auth0
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT"),
                    ),
            )
    }
}
