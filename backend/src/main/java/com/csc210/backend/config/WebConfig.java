package com.csc210.backend.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration public class WebConfig implements WebMvcConfigurer {public void addCorsMappings(CorsRegistry registry){registry.addMapping("/api/**").allowedOriginPatterns("http://localhost:*","http://127.0.0.1:*").allowedMethods("GET","POST","OPTIONS").allowedHeaders("*");}}
