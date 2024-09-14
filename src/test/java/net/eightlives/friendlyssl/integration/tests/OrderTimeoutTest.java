package net.eightlives.friendlyssl.integration.tests;

import net.eightlives.friendlyssl.integration.IntegrationTest;
import net.eightlives.friendlyssl.integration.TestApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.ScheduledExecutorService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = OrderTimeoutTest.class, classes = TestApp.class)
@ActiveProfiles({"test-base", "test-order-timeout"})
class OrderTimeoutTest implements IntegrationTest {

    static {
        Testcontainers.exposeHostPorts(5002, 4430);
    }

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

    @Autowired
    @Qualifier("ssl-certificate-monitor")
    ScheduledExecutorService timer;

    @Override
    public ScheduledExecutorService getTimer() {
        return timer;
    }

    @DisplayName("Start server and certificate order does not respond as valid in time")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void orderTimeout(CapturedOutput output) {
        testLogOutput(
                List.of(
                        "n.e.f.service.AutoRenewService           : Auto-renew starting...",
                        "n.e.f.service.AutoRenewService           : Existing certificate expiration time is",
                        "n.e.f.s.CertificateCreateRenewService    : Starting certificate renew",
                        "n.e.f.service.AcmeAccountService         : Account does not exist. Creating account.",
                        "n.e.f.s.CertificateCreateRenewService    : Certificate account login accessed",
                        "n.e.f.s.CertificateCreateRenewService    : Beginning certificate order.",
                        "n.e.f.service.UpdateCheckerService       : Resource is valid",
                        "n.e.f.s.CertificateCreateRenewService    : Exception while ordering certificate, retry in 1 hours",
                        "net.eightlives.friendlyssl.exception.FriendlySSLException: Exception while handling SSL certificate management",
                        "Caused by: java.util.concurrent.TimeoutException: null"
                ),
                output
        );
    }
}
