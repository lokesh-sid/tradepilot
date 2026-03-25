package tradingbot.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI/Swagger configuration for the Simple Trading Bot API
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trading Bot API")
                        .version("v1.0.0")
                        .description("""
                            REST API for managing multiple futures trading bot instances.
                            
                            Features:
                            - Create and manage multiple independent trading bots
                            - Real-time bot status monitoring
                            - Dynamic leverage and configuration updates
                            - Paper trading mode for risk-free testing
                            - Sentiment analysis integration
                            - Redis-backed persistence for high availability
                            
                            Security:
                            - Input validation on all endpoints
                            - UUID validation for bot IDs
                            - Range validation for numeric parameters (leverage 1-125x)
                            - Global exception handling with RFC 7807 compliance
                            - Consistent error responses with request tracking
                            """)
                        .contact(new Contact()
                                .name("Trading Bot API Support")
                                .email("support@tradingbot.com")
                                .url("https://github.com/lokesh-sid/tradepilot"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development server"),
                        new Server()
                                .url("https://staging-api.tradingbot.com")
                                .description("Staging server"),
                        new Server()
                                .url("https://api.tradingbot.com")
                                .description("Production server")));
    }
}
