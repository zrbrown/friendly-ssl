package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static net.eightlives.friendlyssl.util.TestConstants.EXISTING_KEYSTORE_CERT_EXPIRATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AutoRenewServiceTest {

    private AutoRenewService service;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private SSLCertificateCreateRenewService createRenewService;
    @Mock
    private PKCS12KeyStoreService keyStoreService;
    @Mock
    private Clock clock;

    @BeforeEach
    void setUp() {
        service = new AutoRenewService(config, createRenewService, keyStoreService, clock);

        when(config.getCertificateFriendlyName()).thenReturn("friendly-test");
    }

    @DisplayName("When no certificate exists")
    @Test
    void noCertificate() {
        when(keyStoreService.getCertificate("friendly-test")).thenReturn(Optional.empty());
        CertificateRenewal renewal = new CertificateRenewal(CertificateRenewalStatus.SUCCESS, Instant.now());
        when(createRenewService.createOrRenew(null)).thenReturn(renewal);

        CertificateRenewal result = service.autoRenew();

        assertEquals(renewal, result);
    }

    @DisplayName("When certificate exists")
    @Nested
    class CertificateExists {

        private X509Certificate certificate;

        @BeforeEach
        void setUp() throws CertificateException, IOException {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            certificate = (X509Certificate) certificateFactory.generateCertificate(Files.newInputStream(
                    Path.of("src", "test", "resources", "certificate_chain.pem")));
            when(keyStoreService.getCertificate("friendly-test")).thenReturn(Optional.of(certificate));
            when(config.getAutoRenewalHoursBefore()).thenReturn(3);
        }

        @DisplayName("When certificate is unexpired")
        @Test
        void certificateUnexpired() {
            when(clock.instant()).thenReturn(EXISTING_KEYSTORE_CERT_EXPIRATION
                    .minus(3, ChronoUnit.HOURS)
                    .minus(1, ChronoUnit.SECONDS));

            CertificateRenewal result = service.autoRenew();

            assertEquals(CertificateRenewalStatus.ALREADY_VALID, result.getStatus());
            assertEquals(EXISTING_KEYSTORE_CERT_EXPIRATION.minus(3, ChronoUnit.HOURS), result.getTime());
        }

        @DisplayName("When certificate is expired")
        @Test
        void certificateExpired() {
            when(clock.instant()).thenReturn(EXISTING_KEYSTORE_CERT_EXPIRATION
                    .minus(3, ChronoUnit.HOURS));
            CertificateRenewal renewal = new CertificateRenewal(CertificateRenewalStatus.SUCCESS, Instant.now());
            when(createRenewService.createOrRenew(certificate)).thenReturn(renewal);

            CertificateRenewal result = service.autoRenew();

            assertEquals(renewal, result);
        }
    }
}