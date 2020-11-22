package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.*;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SSLCertificateCreateRenewServiceTest {

    private static final Instant FIXED_CLOCK = Instant.from(OffsetDateTime.of(2020, 2, 3, 4, 5, 6, 0, ZoneOffset.UTC));
    public static final Instant CERT_EXPIRATION = Instant.from(OffsetDateTime.of(2012, 12, 22, 7, 41, 51, 0, ZoneOffset.UTC));
    public static final Instant CERT_RENEWAL = Instant.from(OffsetDateTime.of(2012, 12, 22, 7, 41, 51, 0, ZoneOffset.UTC))
            .minus(72, ChronoUnit.HOURS);

    private SSLCertificateCreateRenewService service;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private AcmeAccountService accountService;
    @Mock
    PKCS12KeyStoreService keyStoreService;
    @Mock
    CertificateOrderHandlerService certificateOrderHandlerService;
    @Mock
    SSLContextService sslContextService;

    @BeforeEach
    void setUp() {
        service = new SSLCertificateCreateRenewService(
                config, accountService, keyStoreService, certificateOrderHandlerService, sslContextService,
                Clock.fixed(FIXED_CLOCK, ZoneId.of("UTC"))
        );
    }

    @DisplayName("When session URL is invalid")
    @Test
    void invalidURL() {
        when(config.getAcmeSessionUrl()).thenReturn("fake");

        assertThrows(IllegalArgumentException.class, () -> service.createOrRenew(null));
    }

    @DisplayName("When session URL is valid")
    @Nested
    class SessionURLValid {

        @BeforeEach
        void setUp() {
            when(config.getAcmeSessionUrl()).thenReturn("acme://letsencrypt.org/staging");
        }

        @DisplayName("When account service throws an exception, CertificateRenewal error should be returned")
        @Test
        void accountServiceException() {
            when(accountService.getOrCreateAccountLogin(any(Session.class))).thenThrow(
                    new SSLCertificateException("")
            );
            when(config.getErrorRetryWaitHours()).thenReturn(2);

            CertificateRenewal renewal = service.createOrRenew(null);

            assertEquals(CertificateRenewalStatus.ERROR, renewal.getStatus());

            assertEquals(FIXED_CLOCK.plus(2, ChronoUnit.HOURS), renewal.getTime());
        }

        @DisplayName("When account service succeeds")
        @Nested
        class AccountServiceSucceeds {

            @Mock
            Login login;
            @Mock
            private org.shredzone.acme4j.Certificate acmeCert;

            private X509Certificate certificate;

            @BeforeEach
            void setUp() throws CertificateException, IOException {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                certificate = (X509Certificate) certificateFactory.generateCertificate(Files.newInputStream(
                        Path.of("src", "test", "resources", "certificate_chain.pem")));

                when(acmeCert.getCertificate()).thenReturn(certificate);
                when(accountService.getOrCreateAccountLogin(any(Session.class))).thenReturn(login);
                when(config.getAutoRenewalHoursBefore()).thenReturn(72);
            }

            @DisplayName("When existing certificate is null")
            @Test
            void emptyCertificate() throws CertificateException, IOException {
                when(certificateOrderHandlerService.handleCertificateOrder(eq(login), any(KeyPair.class)))
                        .thenReturn(acmeCert);

                CertificateRenewal renewal = service.createOrRenew(null);

                verify(sslContextService, times(1)).reloadSSLConfig();
                assertEquals(CertificateRenewalStatus.SUCCESS, renewal.getStatus());
                assertEquals(CERT_RENEWAL, renewal.getTime());
            }

            @DisplayName("When existing certificate is non-null")
            @Nested
            class KeystoreExistingCertificate {

                @BeforeEach
                void setUp() {
                    when(config.getCertificateKeyAlias()).thenReturn("friendlyssl");
                }

                @DisplayName("When keystore service cannot find the certificate by name")
                @Test
                void keystoreNoCertificateFound() throws CertificateException, IOException {
                    service = new SSLCertificateCreateRenewService(
                            config, accountService, keyStoreService, certificateOrderHandlerService, sslContextService,
                            Clock.fixed(CERT_EXPIRATION.minus(3, ChronoUnit.HOURS)
                                    , ZoneId.of("UTC"))
                    );
                    when(keyStoreService.getKeyPair(certificate, "friendlyssl"))
                            .thenReturn(null);
                    when(certificateOrderHandlerService.handleCertificateOrder(eq(login), any(KeyPair.class)))
                            .thenReturn(acmeCert);

                    CertificateRenewal renewal = service.createOrRenew(certificate);

                    verify(sslContextService, times(1)).reloadSSLConfig();
                    assertEquals(CertificateRenewalStatus.SUCCESS, renewal.getStatus());
                    assertEquals(CERT_RENEWAL, renewal.getTime());
                }

                @DisplayName("When keystore service finds the certificate by name")
                @Test
                void keystoreCertificateFound() throws CertificateException, IOException {
                    service = new SSLCertificateCreateRenewService(
                            config, accountService, keyStoreService, certificateOrderHandlerService, sslContextService,
                            Clock.fixed(CERT_EXPIRATION.minus(3, ChronoUnit.HOURS)
                                    , ZoneId.of("UTC"))
                    );
                    KeyPair keyPair = KeyPairUtils.createKeyPair(2048);
                    when(keyStoreService.getKeyPair(certificate, "friendlyssl"))
                            .thenReturn(keyPair);
                    when(certificateOrderHandlerService.handleCertificateOrder(login, keyPair))
                            .thenReturn(acmeCert);

                    CertificateRenewal renewal = service.createOrRenew(certificate);

                    verify(sslContextService, times(1)).reloadSSLConfig();
                    assertEquals(CertificateRenewalStatus.SUCCESS, renewal.getStatus());
                    assertEquals(CERT_RENEWAL, renewal.getTime());
                }
            }
        }
    }
}
