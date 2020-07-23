package net.eightlives.friendlyssl.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.List;

@SpringBootTest
@ContextConfiguration(initializers = MinimumConfigTest.class)
@ActiveProfiles("test-base")
class MinimumConfigTest implements IntegrationTest {

    @Container
    static GenericContainer pebbleContainer = new GenericContainer("letsencrypt/pebble")
            .withCommand("pebble -config /test/my-pebble-config.json")
            .withExposedPorts(14000, 15000)
            .withEnv("PEBBLE_VA_NOSLEEP", "1")
            .withEnv("PEBBLE_WFE_NONCEREJECT", "0")
            .withClasspathResourceMapping(
                    "pebble-config.json",
                    "/test/my-pebble-config.json",
                    BindMode.READ_ONLY
            );

    @Override
    public GenericContainer getPebbleContainer() {
        return pebbleContainer;
    }

    @DisplayName("Start server and token request does not hit ACME challenge endpoint in time")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void minimumConfig(CapturedOutput output) {
        testLogOutput(List.of(
                "n.e.f.s.SSLCertificateCreateRenewService : Starting certificate create/renew",
                "n.e.f.service.AcmeAccountService         : Account does not exist. Terms of service must be accepted in file src/test/resources/temp/tos before account can be created",
                "n.e.f.s.SSLCertificateCreateRenewService : Exception while ordering certificate, retry in 1 hours",
                "org.shredzone.acme4j.exception.AcmeServerException: unable to find existing account for only-return-existing request"
                ),
                output);
    }
}
