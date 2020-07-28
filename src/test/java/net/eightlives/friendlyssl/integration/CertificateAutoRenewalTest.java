package net.eightlives.friendlyssl.integration;

import net.eightlives.friendlyssl.annotation.FriendlySSL;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.util.TestConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static net.eightlives.friendlyssl.integration.CertificateAutoRenewalTest.CertificateRenewalApp;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Pebble cannot currently return a user-defined expiration time (https://github.com/letsencrypt/pebble/issues/251),
 * but when it does another test similar to this one should be created that doesn't use a mock Clock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ContextConfiguration(initializers = CertificateAutoRenewalTest.class, classes = CertificateRenewalApp.class)
@ActiveProfiles({"test-base", "test-tos-accepted", "test-existing-keystore", "test-override-beans"})
@ExtendWith(MockitoExtension.class)
class CertificateAutoRenewalTest implements IntegrationTest {

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
    @Mock
    static Clock clock;

    @Override
    public GenericContainer getPebbleContainer() {
        return pebbleContainer;
    }

    @FriendlySSL
    @SpringBootApplication
    static class CertificateRenewalApp {

        @Bean
        @Primary
        public Clock clock(FriendlySSLConfig config) {
            Instant renewThreshold = TestConstants.EXISTING_KEYSTORE_CERT_EXPIRATION
                    .minus(config.getAutoRenewalHoursBefore(), ChronoUnit.HOURS);
            Instant oneSecondBeforeRenewThreshold = renewThreshold
                    .minus(1, ChronoUnit.SECONDS);

            when(clock.instant())
                    .thenReturn(oneSecondBeforeRenewThreshold)
                    .thenReturn(oneSecondBeforeRenewThreshold)
                    .thenReturn(renewThreshold);

            return clock;
        }
    }

    @DisplayName("Server should auto-renew when auto-renew hours threshold time is crossed")
    @Timeout(20)
    @ExtendWith(OutputCaptureExtension.class)
    @DirtiesContext
    @Test
    void noCertificate(CapturedOutput output) {
        testLogOutputExact(
                List.of(
                        "n.e.f.s.SSLCertificateCreateRenewService : Starting certificate create/renew",
                        "n.e.f.service.AcmeAccountService         : Account does not exist. Creating account.",
                        "n.e.f.s.SSLCertificateCreateRenewService : Certificate account login accessed",
                        "n.e.f.s.SSLCertificateCreateRenewService : Existing certificate expiration time is 2012-12-22T07:41:51Z",
                        "n.e.f.s.SSLCertificateCreateRenewService : Starting certificate create/renew",
                        "n.e.f.service.AcmeAccountService         : Using existing account login",
                        "n.e.f.s.SSLCertificateCreateRenewService : Certificate account login accessed",
                        "n.e.f.s.SSLCertificateCreateRenewService : Beginning certificate order. Renewal: true",
                        "n.e.f.s.SSLCertificateCreateRenewService : Certificate renewal successful. New certificate expiration time is"
                ),
                output
        );

        assertTrue(Files.exists(Path.of(config.getKeystoreFile())));
    }
}
