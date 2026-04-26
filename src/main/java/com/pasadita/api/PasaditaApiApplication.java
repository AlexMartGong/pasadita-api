package com.pasadita.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.pasadita.api.config")
public class PasaditaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PasaditaApiApplication.class, args);
    }

}
