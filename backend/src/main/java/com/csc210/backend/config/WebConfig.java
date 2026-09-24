package com.csc210.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*").allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadLocation = Path.of("uploads").toAbsolutePath().normalize().toUri().toString() + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadLocation);
    }
}
