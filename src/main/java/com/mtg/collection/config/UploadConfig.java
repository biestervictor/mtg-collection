package com.mtg.collection.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Sets the multipart upload size limit to 50 MB.
 *
 * Rationale: Spring Boot's default is 1 MB, which is too small for large
 * DragonShield CSV exports (all-folders.csv can exceed 4 MB).
 * The Helm deployment also sets SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=50MB
 * as an env var, but that only applies when Spring Boot auto-configuration
 * creates the MultipartConfigElement — which it skips when this bean exists.
 * Providing the bean here ensures the limit is correct in all environments
 * including local development without any extra configuration.
 */
@Configuration
public class UploadConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(50));
        factory.setMaxRequestSize(DataSize.ofMegabytes(50));
        return factory.createMultipartConfig();
    }
}
