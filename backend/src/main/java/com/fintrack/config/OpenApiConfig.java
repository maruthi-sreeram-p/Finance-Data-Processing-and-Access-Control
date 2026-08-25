package com.fintrack.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI document and Swagger UI setup.
 *
 * <p>The bearer scheme is declared once and applied as a <em>global</em> security
 * requirement, so every operation inherits it and Swagger UI offers a single
 * "Authorize" box that then applies to all of them. The alternative -- annotating
 * each protected method -- means one forgotten annotation leaves an endpoint
 * undocumented as protected, and anyone testing it through the UI gets a confusing
 * 401 with no way to supply a token.
 *
 * <p>{@code AuthController} opts back out with {@code @SecurityRequirements}, since
 * demanding a token to reach the endpoints that issue tokens would be circular.
 */
@Configuration
public class OpenApiConfig {

    /** Referenced by name from the global requirement and from the components map. */
    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI finTrackOpenApi() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local development")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, bearerScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("FinTrack API")
                .version("v1")
                .description("""
                        Finance data processing and access control platform.

                        ## Authenticating

                        1. `POST /api/auth/register` or `POST /api/auth/login` to obtain a token.
                        2. Press **Authorize** above and paste the `accessToken` value. Swagger \
                        adds the `Bearer ` prefix, so paste the raw token on its own.
                        3. Every other endpoint is then callable from this page.

                        ## Access model

                        - **USER** manages their own transactions and dashboard.
                        - **MANAGER** may additionally read any account's ledger and dashboard \
                        via `?userId=`.
                        - **ADMIN** may do the same, and administer accounts and categories.

                        Read access widens with role; write access never does. Nobody may \
                        create, alter or delete another person's financial records, \
                        administrators included.

                        Requesting a record that exists but belongs to someone else returns \
                        **404**, identical to a record that does not exist, so the API cannot \
                        be used to discover which ids are real.

                        ## Errors

                        Every failure returns the same shape, with a stable `error` code that \
                        clients should branch on in preference to the human-readable `message`.""")
                .contact(new Contact().name("FinTrack"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT"));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                // Purely descriptive, but it tells the UI to present the field as a
                // token rather than as a username/password pair.
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .description("Paste the accessToken returned by /api/auth/login, without the "
                        + "\"Bearer \" prefix.");
    }
}
