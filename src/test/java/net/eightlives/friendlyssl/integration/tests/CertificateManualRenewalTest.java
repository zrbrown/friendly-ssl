package net.eightlives.friendlyssl.integration.tests;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.integration.IntegrationTest;
import net.eightlives.friendlyssl.integration.TestApp;
import net.eightlives.friendlyssl.util.TestUtils;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.Instant;
import java.util.List;

import static net.eightlives.friendlyssl.util.TestUtils.getExpirationContext;
import static net.eightlives.friendlyssl.util.TestUtils.trustAllCertsContext;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = CertificateManualRenewalTest.class, classes = TestApp.class)
@ActiveProfiles({"test-base", "test-tos-accepted", "test-existing-keystore", "test-override-beans", "test-no-auto-renew"})
class CertificateManualRenewalTest implements IntegrationTest {

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

    byte[] keystore;

    @BeforeEach
    void setUp() throws IOException {
        keystore = Files.readAllBytes(Path.of(config.getKeystoreFile()));
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.newOutputStream(Path.of(config.getKeystoreFile())).write(keystore);
    }

    @DisplayName("Renew certificate manually")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void manualRenew(CapturedOutput output) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://localhost:4430/friendly-ssl/certificate/order"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .sslContext(trustAllCertsContext())
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());

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

        testLogOutput(
                List.of(
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

        HttpRequest expirationRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://localhost:4430/"))
                .GET()
                .build();

        TestUtils.SSLContextWithExpiration context = getExpirationContext();
        HttpClient.newBuilder()
                .sslContext(context.getSslContext())
                .build()
                .send(expirationRequest, HttpResponse.BodyHandlers.ofString());

        assertTrue(context.getExpiration().after(Date.from(Instant.now())));
    }
}
