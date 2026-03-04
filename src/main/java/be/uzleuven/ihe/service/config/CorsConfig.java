package be.uzleuven.ihe.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Global CORS configuration for DICOMPolice.
 * Allows cross-origin requests from known viewer origins (e.g. Agfa Xero)
 * so the application can be embedded in iframes and accessed via XHR.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow credentials if needed (set to false when using wildcard origins)
        config.setAllowCredentials(false);

        // Allowed origins – add every host that embeds this app in an iframe
        config.setAllowedOriginPatterns(List.of(
                "https://eideltaws1.med.agfa.be",
                "http://10.229.157.71:*",
                "https://*.med.agfa.be",
                "http://localhost:*"
        ));

        // Allow all standard HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"));

        // Allow all headers (including Authorization, Content-Type, etc.)
        config.setAllowedHeaders(List.of("*"));

        // Expose headers that the browser JS may need to read
        config.setExposedHeaders(List.of(
                "Content-Disposition",
                "Content-Type",
                "Content-Length",
                "X-Request-ID"
        ));

        // Cache preflight response for 30 minutes
        config.setMaxAge(180L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}

