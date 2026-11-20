package dk.greenhour.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI greenHourOpenApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        var keycloak = new SecurityScheme()
            .type(SecurityScheme.Type.OAUTH2)
            .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                .tokenUrl(issuer + "/protocol/openid-connect/token")));

        return new OpenAPI()
            .info(new Info()
                .title("GreenHour API")
                .version("0.1.0")
                .description("Day-ahead prices, CO2 intensity and appliance scheduling for Danish households."))
            .components(new Components().addSecuritySchemes("keycloak", keycloak))
            .addSecurityItem(new SecurityRequirement().addList("keycloak"));
    }
}
