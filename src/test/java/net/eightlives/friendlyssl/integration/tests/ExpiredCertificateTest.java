package net.eightlives.friendlyssl.integration.tests;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.integration.IntegrationTest;
import net.eightlives.friendlyssl.integration.TestApp;
import org.junit.jupiter.api.*;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = ExpiredCertificateTest.class, classes = TestApp.class)
@ActiveProfiles({"test-base", "test-existing-keystore"})
class ExpiredCertificateTest implements IntegrationTest {

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
    FriendlySSLConfig config;

    @Autowired
    @Qualifier("ssl-certificate-monitor")
    ScheduledExecutorService timer;

    @Override
    public ScheduledExecutorService getTimer() {
        return timer;
    }

    byte[] keystore;

    @BeforeEach
    void setUp() throws IOException {
        keystore = Files.readAllBytes(Path.of(config.getKeystoreFile()));
    }

    @AfterEach
    public void tearDown() throws IOException {
        getTimer().shutdownNow();
        Files.newOutputStream(Path.of(config.getKeystoreFile())).write(keystore);
    }

    @DisplayName("Start server and existing certificate is expired")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void expiredCertificate(CapturedOutput output) throws IOException {
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
                        "n.e.f.s.CertificateCreateRenewService    : Finished reloading SSL context"
                ),
                output
        );

        byte[] newKeystore = Files.readAllBytes(Path.of(config.getKeystoreFile()));

        if (keystore.length == newKeystore.length) {
            for (int i = 0; i < newKeystore.length; i++) {
                if (newKeystore[i] != keystore[i]) {
                    break;
                }
                if (i == newKeystore.length - 1) {
                    fail("Keystore should have changed after certificate renewal");
                }
            }
        }
    }
}
