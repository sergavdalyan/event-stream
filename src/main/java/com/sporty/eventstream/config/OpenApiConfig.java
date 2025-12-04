package com.sporty.eventstream.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8088}")
    private String serverPort;

    @Bean
    public OpenAPI eventStreamOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:" + serverPort);

        Info info = new Info()
                .title("Event Stream API")
                .version("1.0.0")
                .description("Real-time event score streaming service. ");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}

