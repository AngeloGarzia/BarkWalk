package com.barkwalk.config;

import com.barkwalk.auth.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Configuration technique partagée (encodeur de mots de passe, beans transverses).
@Configuration
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(JwtProperties.class)
public class AppConfig {
}
