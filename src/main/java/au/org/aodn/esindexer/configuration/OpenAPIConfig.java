package au.org.aodn.esindexer.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {
    /**
     * Config to setup X-API-Key secret so that client call needs this secret to invoke Indexer RestAPI
     * @return
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("X-API-Key",
                                new SecurityScheme()
                                        .name("X-API-Key")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .scheme("ApiKeyAuth")
                                        .bearerFormat("JWT")))
                .info(new Info()
                        .title("ES Indexer API")
                        .description("API for the Portal Elasticsearch Indexer")
                );
    }
}
