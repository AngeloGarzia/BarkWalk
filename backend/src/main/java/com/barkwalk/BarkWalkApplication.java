package com.barkwalk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Point d'entrée Spring Boot de l'API Bark & Walk.
@SpringBootApplication
public class BarkWalkApplication {

    public static void main(String[] args) {
        SpringApplication.run(BarkWalkApplication.class, args);
    }
}
