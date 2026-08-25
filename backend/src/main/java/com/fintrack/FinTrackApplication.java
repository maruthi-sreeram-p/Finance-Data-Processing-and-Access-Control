package com.fintrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Entry point for the FinTrack backend.
 *
 * <p>JPA auditing is enabled here so {@code @CreatedDate} / {@code @LastModifiedDate}
 * on entities are populated by Hibernate instead of being set by hand in services.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
public class FinTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinTrackApplication.class, args);
    }
}
