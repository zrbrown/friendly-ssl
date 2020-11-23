package net.eightlives.friendlyssl.integration.tests;

import net.eightlives.friendlyssl.integration.IntegrationTest;
import net.eightlives.friendlyssl.integration.TestApp;
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
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = SlowServerTest.class, classes = TestApp.class)
@ActiveProfiles({"test-base", "test-tos-accepted"})
class SlowServerTest implements IntegrationTest {

    static {
        Testcontainers.exposeHostPorts(5002, 4430);
    }

    static GenericContainer pebbleContainer = new GenericContainer("letsencrypt/pebble")
            .withCommand("pebble -config /test/my-pebble-config.json")
            .withExposedPorts(14000, 15000)
            .withEnv("PEBBLE_VA_NOSLEEP", "0")
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

    @DisplayName("Start server and ACME server responses have variable latency")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void slowServer(CapturedOutput output) {
        testLogOutput(
                List.of(
                        "n.e.f.service.AutoRenewService           : Auto-renew starting...",
                        "n.e.f.service.AutoRenewService           : Existing certificate expiration time is",
                        "n.e.f.s.CertificateCreateRenewService    : Starting certificate renew",
                        "n.e.f.service.AcmeAccountService         : Account does not exist. Creating account.",
                        "n.e.f.s.CertificateCreateRenewService    : Certificate account login accessed",
                        "n.e.f.s.CertificateCreateRenewService    : Beginning certificate order.",
                        "n.e.f.service.UpdateCheckerService       : Resource is valid",
                        "n.e.f.s.CertificateCreateRenewService    : Certificate renewal successful. New certificate expiration time is",
                        "n.e.f.s.CertificateCreateRenewService    : Reloading SSL context...",
                        "n.e.f.service.SSLContextService          : Finished reloading SSL context"
                ),
                output
        );
    }
}
