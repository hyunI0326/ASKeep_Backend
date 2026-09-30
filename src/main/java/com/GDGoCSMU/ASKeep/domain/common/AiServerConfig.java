package com.GDGoCSMU.ASKeep.domain.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiServerConfig {
    @Bean
    public RestClient airestClient(RestClient.Builder builder, @Value("${ai.server.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
