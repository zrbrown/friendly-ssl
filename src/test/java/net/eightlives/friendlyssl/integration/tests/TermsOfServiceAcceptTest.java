package net.eightlives.friendlyssl.integration.tests;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.integration.IntegrationTest;
import net.eightlives.friendlyssl.integration.TestApp;
import net.eightlives.friendlyssl.util.TestConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import java.util.List;

import static net.eightlives.friendlyssl.util.TestUtils.trustAllCertsContext;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = TermsOfServiceAcceptTest.class, classes = TestApp.class)
@ActiveProfiles({"test-base"})
class TermsOfServiceAcceptTest implements IntegrationTest {

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

    @DisplayName("After failing to login to account, accept TOS and renew certificate")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void acceptTos(CapturedOutput output) throws IOException, InterruptedException {
        testLogOutput(
                List.of(
                        "n.e.f.service.AutoRenewService           : Auto-renew starting...",
                        "n.e.f.service.AutoRenewService           : Existing certificate expiration time is",
                        "n.e.f.s.CertificateCreateRenewService    : Starting certificate renew",
                        "n.e.f.s.CertificateCreateRenewService    : Exception while ordering certificate, retry in 1 hours",
                        "net.eightlives.friendlyssl.exception.FriendlySSLException: Exception while handling SSL certificate management: Account does not exist. Terms of service must be accepted in file src/test/resources/temp/tos before account can be created"
                ),
                output
        );

        HttpRequest tosRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://localhost:4430/friendly-ssl/tos/agree"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{ \"termsOfServiceLink\": \"" + TestConstants.PEBBLE_TOS_LINK + "\" }"
                ))
                .build();
        HttpResponse<String> tosResponse = HttpClient.newBuilder()
                .sslContext(trustAllCertsContext())
                .build()
                .send(tosRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, tosResponse.statusCode());

        HttpRequest orderRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://localhost:4430/friendly-ssl/certificate/order"))
                .GET()
                .build();
        HttpResponse<String> orderResponse = HttpClient.newBuilder()
                .sslContext(trustAllCertsContext())
                .build()
                .send(orderRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, orderResponse.statusCode());

        testLogOutputExact(
                List.of(
                        "n.e.f.service.AutoRenewService           : Auto-renew starting...",
                        "n.e.f.service.AutoRenewService           : Existing certificate expiration time is",
                        "n.e.f.s.CertificateCreateRenewService    : Starting certificate renew",
                        "n.e.f.s.CertificateCreateRenewService    : Exception while ordering certificate, retry in 1 hours",
                        "net.eightlives.friendlyssl.exception.FriendlySSLException: Exception while handling SSL certificate management: Account does not exist. Terms of service must be accepted in file src/test/resources/temp/tos before account can be created",
                        "n.e.f.s.CertificateCreateRenewService    : Starting certificate renew",
                        "n.e.f.service.AcmeAccountService         : Account does not exist. Creating account.",
                        "n.e.f.s.CertificateCreateRenewService    : Certificate account login accessed",
                        "n.e.f.s.CertificateCreateRenewService    : Beginning certificate order.",
                        "n.e.f.s.CertificateCreateRenewService    : Certificate renewal successful. New certificate expiration time is",
                        "n.e.f.s.CertificateCreateRenewService    : Reloading SSL context...",
                        "n.e.f.service.SSLContextService          : Finished reloading SSL context"
                ),
                output
        );
    }
}
