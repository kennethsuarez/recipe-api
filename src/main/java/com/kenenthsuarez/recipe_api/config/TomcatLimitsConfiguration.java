package com.kenenthsuarez.recipe_api.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatLimitsConfiguration {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> boundedTomcat(
            @Value("${recipe.http-task-queue-size:32}") int queueSize) {
        if (queueSize < 1) throw new IllegalArgumentException("HTTP task queue size must be positive");
        return factory -> factory.addConnectorCustomizers(connector -> {
            if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> protocol) {
                protocol.setMaxQueueSize(queueSize);
                protocol.setDisableUploadTimeout(false);
                protocol.setConnectionUploadTimeout(5000);
            }
        });
    }
}
