package com.fintrack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: fails the build if the Spring context cannot be built at all --
 * a missing bean, a broken migration, or an entity that no longer matches the
 * schema all surface here before any feature test runs.
 */
@SpringBootTest
@ActiveProfiles("test")
class FinTrackApplicationTests {

    @Test
    void contextLoads() {
        // Assertion-free by design: construction of the application context is the assertion.
    }
}
