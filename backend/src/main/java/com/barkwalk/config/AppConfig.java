package com.barkwalk.config;

import com.barkwalk.auth.jwt.JwtProperties;
import com.barkwalk.common.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

// Configuration technique partagée (encodeur de mots de passe, beans transverses).
@Configuration
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, MailProperties.class})
public class AppConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
