package com.example.supermarket.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI supermarketOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("超市购物系统 API")
                .description("Supermarket shopping system backend API (Spring Boot + JWT)")
                .version("v0.1.0"));
    }
}
