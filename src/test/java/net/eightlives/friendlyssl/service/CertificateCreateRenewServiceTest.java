package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.FriendlySSLException;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.boot.autoconfigure.ssl.SslProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.server.Ssl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
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
    private PKCS12KeyStoreService keyStoreService;
    @Mock
    private CertificateOrderHandlerService certificateOrderHandlerService;
    @Mock
    private SslBundle sslBundle;
    @Mock()
    private ServerProperties serverConfig;
    @Mock()
    private SslProperties sslConfig;
    @Mock
    private SslBundles sslBundles;

    @BeforeEach
    void setUp() {
        service = new CertificateCreateRenewService(
                config, serverConfig, sslConfig, accountService, keyStoreService, certificateOrderHandlerService,
                Clock.fixed(FIXED_CLOCK, ZoneId.of("UTC")), sslBundles
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
            when(config.getErrorRetryWaitHours()).thenReturn(2);
            when(accountService.getOrCreateAccountLogin(any(Session.class))).thenThrow(
                    new FriendlySSLException("")
            );

            CertificateRenewal renewal = serviceCall.apply(service);

            assertEquals(CertificateRenewalStatus.ERROR, renewal.status());
            assertEquals(FIXED_CLOCK.plus(2, ChronoUnit.HOURS), renewal.time());
        }

        @DisplayName("When account service succeeds")
        @Nested
        class AccountServiceSucceeds {

            @Mock
            Login login;
            @Mock
            private org.shredzone.acme4j.Certificate acmeCert;

            @BeforeEach
            void setUp() {
                when(accountService.getOrCreateAccountLogin(any(Session.class))).thenReturn(login);
            }

            @DisplayName("When server.ssl is not configured, ")
            @ParameterizedTest(name = "for method {0}")
            @ArgumentsSource(ServiceCallProvider.class)
            void serverSslNotConfigured(Function<CertificateCreateRenewService, CertificateRenewal> serviceCall) {
                when(config.getErrorRetryWaitHours()).thenReturn(2);

                CertificateRenewal renewal = serviceCall.apply(service);

                assertEquals(CertificateRenewalStatus.ERROR, renewal.status());
                assertEquals(FIXED_CLOCK.plus(2, ChronoUnit.HOURS), renewal.time());
            }

            @DisplayName("When server.ssl is configured")
            @Nested
            class SSLConfigured {

                @Mock
                private Ssl ssl;

                @BeforeEach
                void setUp() {
                    when(serverConfig.getSsl()).thenReturn(ssl);
                }

                @DisplayName("When server.ssl.bundle is not configured, ")
                @ParameterizedTest(name = "for method {0}")
                @ArgumentsSource(ServiceCallProvider.class)
                void serverSslBundleNotConfigured(Function<CertificateCreateRenewService, CertificateRenewal> serviceCall) {
                    when(config.getErrorRetryWaitHours()).thenReturn(2);

                    CertificateRenewal renewal = serviceCall.apply(service);

                    assertEquals(CertificateRenewalStatus.ERROR, renewal.status());
                    assertEquals(FIXED_CLOCK.plus(2, ChronoUnit.HOURS), renewal.time());
                }

                @DisplayName("When server.ssl.bundle is configured")
                @Nested
                class SSLBundleConfigured {

                    @BeforeEach
                    void setUp() {
                        when(ssl.getBundle()).thenReturn("friendlyssl");
                    }

                    @DisplayName("When SSL bundle cannot be found, ")
                    @ParameterizedTest(name = "for method {0}")
                    @ArgumentsSource(ServiceCallProvider.class)
                    void serverSslBundleNotFound(Function<CertificateCreateRenewService, CertificateRenewal> serviceCall) {
                        when(config.getErrorRetryWaitHours()).thenReturn(2);

                        CertificateRenewal renewal = serviceCall.apply(service);

                        assertEquals(CertificateRenewalStatus.ERROR, renewal.status());
                        assertEquals(FIXED_CLOCK.plus(2, ChronoUnit.HOURS), renewal.time());
                    }

                    @DisplayName("When SSL bundle is found")
                    @Nested
                    class SSLBundleFound {

                        @Captor
                        private ArgumentCaptor<Consumer<SslBundle>> handlerCaptor;

                        @BeforeEach
                        void setUp() {
                            doNothing().when(sslBundles).addBundleUpdateHandler(eq("friendlyssl"), handlerCaptor.capture());
                        }

                        @DisplayName("When certificate order fails, ")
                        @ParameterizedTest(name = "for method {0}")
                        @ArgumentsSource(ServiceCallProvider.class)
                        void certificateOrderFails(Function<CertificateCreateRenewService, CertificateRenewal> serviceCall) {
                            when(config.getErrorRetryWaitHours()).thenReturn(2);
                            when(certificateOrderHandlerService.handleCertificateOrder(any(), any())).thenThrow(new FriendlySSLException("error"));

                            CertificateRenewal renewal = serviceCall.apply(service);

                            assertEquals(CertificateRenewalStatus.ERROR, renewal.status());
                            assertEquals(FIXED_CLOCK.plus(2, ChronoUnit.HOURS), renewal.time());
                        }

                        @DisplayName("When certificate order succeeds")
                        @Nested
                        class CertificateOrderSucceeds {

                            @BeforeEach
                            void setUp() throws IOException, CertificateException {
                                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                                X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(Files.newInputStream(
                                        Path.of("src", "test", "resources", "certificate_chain.pem")));
                                when(acmeCert.getCertificate()).thenReturn(certificate);
                                when(certificateOrderHandlerService.handleCertificateOrder(eq(login), any(KeyPair.class)))
                                        .thenReturn(acmeCert);
                            }

                            @DisplayName("When spring.ssl.bundle is not configured, ")
                            @ParameterizedTest(name = "for method {0}")
                            @ArgumentsSource(ServiceCallProvider.class)
                            void springSslBundleNotConfigured(Function<CertificateCreateRenewService, CertificateRenewal> serviceCall) {
                                when(config.getErrorRetryWaitHours()).thenReturn(2);

                                CertificateRenewal renewal = serviceCall.apply(service);

                                assertEquals(CertificateRenewalStatus.ERROR, renewal.status());
                                assertEquals(FIXED_CLOCK.plus(2, ChronoUnit.HOURS), renewal.time());
                            }

                            @DisplayName("When spring.ssl.bundle is configured, ")
                            @Nested
                            class SpringSSLBundleConfigured {

                                @BeforeEach
                                void setUp() {
                                    SslProperties.Bundles bundles = mock(SslProperties.Bundles.class);
                                    SslProperties.Bundles.Watch watch = mock(SslProperties.Bundles.Watch.class);
                                    SslProperties.Bundles.Watch.File watchFile = mock(SslProperties.Bundles.Watch.File.class);
                                    when(watchFile.getQuietPeriod()).thenReturn(Duration.ofSeconds(2));
                                    when(watch.getFile()).thenReturn(watchFile);
                                    when(bundles.getWatch()).thenReturn(watch);
                                    when(sslConfig.getBundle()).thenReturn(bundles);
                                }

                                @DisplayName("When calling ::createCertificate")
                                @Nested
                                class CreateCertificate {

                                    @DisplayName("When calling ::createCertificate and SSL Bundle change is not picked up within the quiet period")
                                    @Test
                                    void createCertificateTimeout() {
                                        when(config.getErrorRetryWaitHours()).thenReturn(2);

                                        CertificateRenewal renewal = service.createCertificate();

                                        assertEquals(CertificateRenewalStatus.ERROR, renewal.status());
                                        assertEquals(FIXED_CLOCK.plus(2, ChronoUnit.HOURS), renewal.time());
                                    }

                                    @DisplayName("When calling ::createCertificate")
                                    @Test
                                    void createCertificate() {
                                        when(config.getAutoRenewalHoursBefore()).thenReturn(72);

                                        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                                            executor.submit(() -> {
                                                try {
                                                    Thread.sleep(1000);
                                                } catch (InterruptedException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                handlerCaptor.getValue().accept(null);
                                            });

                                            CertificateRenewal renewal = service.createCertificate();

                                            assertEquals(CertificateRenewalStatus.SUCCESS, renewal.status());
                                            assertEquals(CERT_RENEWAL, renewal.time());
                                        }
                                    }
                                }

                                @DisplayName("When calling #renewCertificate")
                                @Nested
                                class RenewCertificate {

                                    @BeforeEach
                                    void setUp() {
                                        when(config.getCertificateKeyAlias()).thenReturn("friendlyssl");
                                        when(config.getAutoRenewalHoursBefore()).thenReturn(72);
                                    }

                                    @DisplayName("When keystore service cannot find the certificate by name")
                                    @Test
                                    void keystoreNoCertificateFound() {
                                        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                                            service = new CertificateCreateRenewService(
                                                    config, serverConfig, sslConfig, accountService, keyStoreService, certificateOrderHandlerService,
                                                    Clock.fixed(CERT_EXPIRATION.minus(3, ChronoUnit.HOURS), ZoneId.of("UTC")),
                                                    sslBundles
                                            );
                                            when(keyStoreService.getKeyPair("friendlyssl"))
                                                    .thenReturn(null);

                                            executor.submit(() -> {
                                                try {
                                                    Thread.sleep(1000);
                                                } catch (InterruptedException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                handlerCaptor.getValue().accept(null);
                                            });
                                            CertificateRenewal renewal = service.renewCertificate();

                                            assertEquals(CertificateRenewalStatus.SUCCESS, renewal.status());
                                            assertEquals(CERT_RENEWAL, renewal.time());
                                        }
                                    }

                                    @DisplayName("When keystore service finds the certificate by name")
                                    @Test
                                    void keystoreCertificateFound() {
                                        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                                            service = new CertificateCreateRenewService(
                                                    config, serverConfig, sslConfig, accountService, keyStoreService, certificateOrderHandlerService,
                                                    Clock.fixed(CERT_EXPIRATION.minus(3, ChronoUnit.HOURS), ZoneId.of("UTC")),
                                                    sslBundles
                                            );
                                            KeyPair keyPair = KeyPairUtils.createKeyPair(2048);
                                            when(keyStoreService.getKeyPair("friendlyssl"))
                                                    .thenReturn(keyPair);

                                            executor.submit(() -> {
                                                try {
                                                    Thread.sleep(1000);
                                                } catch (InterruptedException e) {
                                                    throw new RuntimeException(e);
                                                }
                                                handlerCaptor.getValue().accept(null);
                                            });
                                            CertificateRenewal renewal = service.renewCertificate();

                                            assertEquals(CertificateRenewalStatus.SUCCESS, renewal.status());
                                            assertEquals(CERT_RENEWAL, renewal.time());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static class ServiceCallProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(Named.of("::createCertificate", (Function<CertificateCreateRenewService, CertificateRenewal>) CertificateCreateRenewService::createCertificate)),
                    Arguments.of(Named.of("::renewCertificate", (Function<CertificateCreateRenewService, CertificateRenewal>) CertificateCreateRenewService::renewCertificate))
            );
        }
    }
}
