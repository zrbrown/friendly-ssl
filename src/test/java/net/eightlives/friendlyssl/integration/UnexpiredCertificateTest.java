package net.eightlives.friendlyssl.integration;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = UnexpiredCertificateTest.class, classes = TestApp.class)
@ActiveProfiles({"test-base", "test-tos-accepted"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnexpiredCertificateTest implements IntegrationTest {

    static {
        Testcontainers.exposeHostPorts(5002);
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

    @Autowired
    FriendlySSLConfig config;

    @Override
    public GenericContainer getPebbleContainer() {
        return pebbleContainer;
    }

    File keystore;

    @Order(1)
    @DisplayName("Start server and certificate does not exist")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void noCertificate(CapturedOutput output) {
        testLogOutput(
                List.of(
                        "n.e.f.s.SSLCertificateCreateRenewService : Starting certificate create/renew",
                        "n.e.f.service.AcmeAccountService         : Account does not exist. Creating account.",
                        "n.e.f.s.SSLCertificateCreateRenewService : Certificate account login accessed",
                        "n.e.f.s.SSLCertificateCreateRenewService : Beginning certificate order. Renewal: false",
                        "n.e.f.service.UpdateCheckerService       : Resource is valid",
                        "n.e.f.s.SSLCertificateCreateRenewService : Certificate renewal successful. New certificate expiration time is"
                ),
                output
        );

        assertTrue(Files.exists(Path.of(config.getKeystoreFile())));

        keystore = Path.of(config.getKeystoreFile()).toFile();
    }

    @Order(2)
    @DisplayName("Then start server and unexpired certificate exists")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void unexpiredCertificateExists(CapturedOutput output) {
        testLogOutput(
                List.of(
                        "n.e.f.s.SSLCertificateCreateRenewService : Starting certificate create/renew",
                        "n.e.f.service.AcmeAccountService         : Using existing account login",
                        "n.e.f.s.SSLCertificateCreateRenewService : Certificate account login accessed",
                        "n.e.f.s.SSLCertificateCreateRenewService : Existing certificate expiration time is"
                ),
                output
        );

        assertEquals(keystore, Path.of(config.getKeystoreFile()).toFile());
    }
}
