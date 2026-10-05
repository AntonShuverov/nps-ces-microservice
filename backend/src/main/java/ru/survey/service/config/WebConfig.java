package ru.survey.service.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS нужен, только если виджет обращается к сервису с другого домена.
 * Если сервис доступен через gateway на домене сайта, список оставляется пустым.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${survey.cors-allowed-origins:}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!allowedOrigins.isEmpty()) {
            registry.addMapping("/api/**")
                    .allowedOrigins(allowedOrigins.toArray(String[]::new))
                    .allowedMethods("GET", "POST", "PUT")
                    .allowCredentials(true);
        }
    }
}
