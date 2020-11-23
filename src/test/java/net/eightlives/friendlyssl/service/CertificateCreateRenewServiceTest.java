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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
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
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateCreateRenewServiceTest {

    private static final Instant FIXED_CLOCK = Instant.from(OffsetDateTime.of(2020, 2, 3, 4, 5, 6, 0, ZoneOffset.UTC));
    public static final Instant CERT_EXPIRATION = Instant.from(OffsetDateTime.of(2012, 12, 22, 7, 41, 51, 0, ZoneOffset.UTC));
    public static final Instant CERT_RENEWAL = Instant.from(OffsetDateTime.of(2012, 12, 22, 7, 41, 51, 0, ZoneOffset.UTC))
            .minus(72, ChronoUnit.HOURS);

    private CertificateCreateRenewService service;

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
        service = new CertificateCreateRenewService(
                config, accountService, keyStoreService, certificateOrderHandlerService, sslContextService,
                Clock.fixed(FIXED_CLOCK, ZoneId.of("UTC"))
        );
    }

    @DisplayName("When session URL is invalid")
    @ParameterizedTest(name = "for method {0}")
    @ArgumentsSource(ServiceCallProvider.class)
    void invalidURL(Function<CertificateCreateRenewService, CertificateRenewal> serviceCall) {
        when(config.getAcmeSessionUrl()).thenReturn("fake");

        assertThrows(IllegalArgumentException.class, () -> serviceCall.apply(service));
    }

    @DisplayName("When session URL is valid")
    @Nested
    class SessionURLValid {

        @BeforeEach
        void setUp() {
            when(config.getAcmeSessionUrl()).thenReturn("acme://letsencrypt.org/staging");
        }

        @DisplayName("When account service throws an exception, CertificateRenewal error should be returned")
        @ParameterizedTest(name = "for method {0}")
        @ArgumentsSource(ServiceCallProvider.class)
        void accountServiceException(Function<CertificateCreateRenewService, CertificateRenewal> serviceCall) {
            when(accountService.getOrCreateAccountLogin(any(Session.class))).thenThrow(
                    new SSLCertificateException("")
            );
            when(config.getErrorRetryWaitHours()).thenReturn(2);

            CertificateRenewal renewal = serviceCall.apply(service);

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

            @BeforeEach
            void setUp() throws CertificateException, IOException {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(Files.newInputStream(
                        Path.of("src", "test", "resources", "certificate_chain.pem")));

                when(acmeCert.getCertificate()).thenReturn(certificate);
                when(accountService.getOrCreateAccountLogin(any(Session.class))).thenReturn(login);
                when(config.getAutoRenewalHoursBefore()).thenReturn(72);
            }

            @DisplayName("When calling #createCertificate")
            @Test
            void createCertificate() {
                when(certificateOrderHandlerService.handleCertificateOrder(eq(login), any(KeyPair.class)))
                        .thenReturn(acmeCert);

                CertificateRenewal renewal = service.createCertificate();

                verify(sslContextService, times(1)).reloadSSLConfig();
                assertEquals(CertificateRenewalStatus.SUCCESS, renewal.getStatus());
                assertEquals(CERT_RENEWAL, renewal.getTime());
            }

            @DisplayName("When calling #renewCertificate")
            @Nested
            class RenewCertificate {

                @BeforeEach
                void setUp() {
                    when(config.getCertificateKeyAlias()).thenReturn("friendlyssl");
                }

                @DisplayName("When keystore service cannot find the certificate by name")
                @Test
                void keystoreNoCertificateFound() {
                    service = new CertificateCreateRenewService(
                            config, accountService, keyStoreService, certificateOrderHandlerService, sslContextService,
                            Clock.fixed(CERT_EXPIRATION.minus(3, ChronoUnit.HOURS)
                                    , ZoneId.of("UTC"))
                    );
                    when(keyStoreService.getKeyPair("friendlyssl"))
                            .thenReturn(null);
                    when(certificateOrderHandlerService.handleCertificateOrder(eq(login), any(KeyPair.class)))
                            .thenReturn(acmeCert);

                    CertificateRenewal renewal = service.renewCertificate();

                    verify(sslContextService, times(1)).reloadSSLConfig();
                    assertEquals(CertificateRenewalStatus.SUCCESS, renewal.getStatus());
                    assertEquals(CERT_RENEWAL, renewal.getTime());
                }

                @DisplayName("When keystore service finds the certificate by name")
                @Test
                void keystoreCertificateFound() {
                    service = new CertificateCreateRenewService(
                            config, accountService, keyStoreService, certificateOrderHandlerService, sslContextService,
                            Clock.fixed(CERT_EXPIRATION.minus(3, ChronoUnit.HOURS)
                                    , ZoneId.of("UTC"))
                    );
                    KeyPair keyPair = KeyPairUtils.createKeyPair(2048);
                    when(keyStoreService.getKeyPair("friendlyssl"))
                            .thenReturn(keyPair);
                    when(certificateOrderHandlerService.handleCertificateOrder(login, keyPair))
                            .thenReturn(acmeCert);

                    CertificateRenewal renewal = service.renewCertificate();

                    verify(sslContextService, times(1)).reloadSSLConfig();
                    assertEquals(CertificateRenewalStatus.SUCCESS, renewal.getStatus());
                    assertEquals(CERT_RENEWAL, renewal.getTime());
                }
            }
        }
    }

    static class ServiceCallProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of((Function<CertificateCreateRenewService, CertificateRenewal>) CertificateCreateRenewService::createCertificate),
                    Arguments.of((Function<CertificateCreateRenewService, CertificateRenewal>) CertificateCreateRenewService::renewCertificate)
            );
        }
    }
}
