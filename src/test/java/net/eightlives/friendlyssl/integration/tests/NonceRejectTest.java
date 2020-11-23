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

@SpringBootTest
@ContextConfiguration(initializers = NonceRejectTest.class, classes = TestApp.class)
@ActiveProfiles({"test-base", "test-tos-accepted"})
class NonceRejectTest implements IntegrationTest {

    static {
        Testcontainers.exposeHostPorts(5002, 4430);
    }

    static GenericContainer pebbleContainer = new GenericContainer("letsencrypt/pebble")
            .withCommand("pebble -config /test/my-pebble-config.json")
            .withExposedPorts(14000, 15000)
            .withEnv("PEBBLE_VA_NOSLEEP", "1")
            .withEnv("PEBBLE_WFE_NONCEREJECT", "100")
            .withClasspathResourceMapping(
                    "pebble-config.json",
                    "/test/my-pebble-config.json",
                    BindMode.READ_ONLY
            );

    @Override
    public GenericContainer getPebbleContainer() {
        return pebbleContainer;
    }

    @DisplayName("Start server and every nonce is rejected")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void nonceReject(CapturedOutput output) {
        testLogOutput(
                List.of(
                        "n.e.f.service.AutoRenewService           : Auto-renew starting...",
                        "n.e.f.service.AutoRenewService           : Existing certificate expiration time is",
                        "n.e.f.s.CertificateCreateRenewService    : Starting certificate renew",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 1/10)",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 2/10)",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 3/10)",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 4/10)",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 5/10)",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 6/10)",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 7/10)",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 8/10)",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 9/10)",
                        "o.s.acme4j.connector.DefaultConnection   : Bad Replay Nonce, trying again (attempt 10/10)",
                        "n.e.f.service.AcmeAccountService         : Error while retrieving or creating ACME Login",
                        "n.e.f.s.CertificateCreateRenewService    : Exception while ordering certificate, retry in 1 hours",
                        "net.eightlives.friendlyssl.exception.SSLCertificateException: Exception while handling SSL certificate management",
                        "Caused by: org.shredzone.acme4j.exception.AcmeException: Too many reattempts",
                        "Caused by: org.shredzone.acme4j.exception.AcmeServerException: JWS has an invalid anti-replay nonce:"
                ),
                output
        );
    }
}
