package net.eightlives.friendlyssl.integration;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AcmeServerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Container
    static GenericContainer container = new GenericContainer("letsencrypt/pebble")
            .withCommand("pebble -config /test/my-pebble-config.json")
            .withExposedPorts(14000, 15000)
            .withEnv("PEBBLE_VA_NOSLEEP", "1")
            .withClasspathResourceMapping(
                    "pebble-config.json",
                    "/test/my-pebble-config.json",
                    BindMode.READ_ONLY
            );

    @Override
    public void initialize(@NotNull ConfigurableApplicationContext applicationContext) {
        container.start();
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "friendly-ssl.acme-session-url=acme://pebble/localhost:" + container.getMappedPort(14000)
        );
    }
}
