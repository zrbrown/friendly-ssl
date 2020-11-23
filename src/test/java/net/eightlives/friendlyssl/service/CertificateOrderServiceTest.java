package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.FriendlySSLException;
import net.eightlives.friendlyssl.exception.UpdateFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateOrderServiceTest {

    private static final String DOMAIN = "domain.com";
    private static final byte[] CSR = "this is a certificate signing request".getBytes();
    private static final int ORDER_TIMEOUT_SECONDS = 3;

    private CertificateOrderService service;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private ChallengeProcessorService challengeProcessorService;
    @Mock
    private CSRService csrService;
    @Mock
    private UpdateCheckerService updateCheckerService;
    @Mock
    private Login login;
    @Mock
    private Account account;
    @Mock
    private OrderBuilder orderBuilder;

    private KeyPair domainKeyPair;

    @BeforeEach
    void setUp() throws IOException {
        when(login.getAccount()).thenReturn(account);
        when(account.newOrder()).thenReturn(orderBuilder);
        when(orderBuilder.domain(anyString())).thenReturn(orderBuilder);
        domainKeyPair = KeyPairUtils.readKeyPair(Files.newBufferedReader(
                Path.of("src", "test", "resources", "keypair.pem")));
        service = new CertificateOrderService(config, challengeProcessorService, csrService, updateCheckerService);
    }

    @DisplayName("When account creation throws an exception")
    @Test
    void accountCreationFails() throws AcmeException {
        when(orderBuilder.create()).thenThrow(new AcmeException());

        assertThrows(FriendlySSLException.class, () -> service.orderCertificate(DOMAIN, login, domainKeyPair));
    }

    @DisplayName("When account creation succeeds")
    @Nested
    class AccountCreationSucceeds {

        @Mock
        private Order order;

        private final List<Authorization> authorizations = Collections.emptyList();

        @BeforeEach
        void setUp() throws AcmeException {
            when(order.getAuthorizations()).thenReturn(authorizations);
            when(orderBuilder.create()).thenReturn(order);
        }

        @DisplayName("and challenge processor throws an exception")
        @Test
        void challengeProcessorFails() {
            when(challengeProcessorService.process(authorizations)).thenThrow(new FriendlySSLException(""));

            assertThrows(FriendlySSLException.class, () -> service.orderCertificate(DOMAIN, login, domainKeyPair));
        }

        @DisplayName("and challenge is processed successfully")
        @Nested
        class ChallengeProcessed {

            @Mock
            private CompletableFuture<Void> challengeProcessorFuture;

            @BeforeEach
            void setUp() {
                when(challengeProcessorService.process(authorizations))
                        .thenReturn(challengeProcessorFuture);
            }

            @DisplayName("and future retrieval fails")
            @ParameterizedTest(name = "with exception {0}")
            @ValueSource(classes = {InterruptedException.class, ExecutionException.class, CancellationException.class})
            void challengeProcessorFutureFails(Class<Throwable> exceptionClass) throws ExecutionException, InterruptedException {
                when(challengeProcessorFuture.get()).thenThrow(exceptionClass);

                assertThrows(FriendlySSLException.class, () -> service.orderCertificate(DOMAIN, login, domainKeyPair));
            }

            @DisplayName("and future retrieval succeeds")
            @Nested
            class ChallengeProcessRetrieved {

                @BeforeEach
                void setUp() throws ExecutionException, InterruptedException {
                    when(challengeProcessorFuture.get()).thenReturn(null);
                }

                @DisplayName("and CSR service throws an exception")
                @Test
                void csrServiceFails() {
                    when(csrService.generateCSR(DOMAIN, domainKeyPair))
                            .thenThrow(new FriendlySSLException(""));

                    assertThrows(FriendlySSLException.class, () -> service.orderCertificate(DOMAIN, login, domainKeyPair));
                }

                @DisplayName("and CSR service succeeds")
                @Nested
                class CSRServiceSucceeds {

                    @BeforeEach
                    void setUp() {
                        when(csrService.generateCSR(DOMAIN, domainKeyPair))
                                .thenReturn("this is a certificate signing request".getBytes());
                    }

                    @DisplayName("and order execution throws an exception")
                    @Test
                    void orderExecutionFails() throws AcmeException {
                        doThrow(new AcmeException()).when(order).execute(CSR);

                        assertThrows(FriendlySSLException.class, () -> service.orderCertificate(DOMAIN, login, domainKeyPair));
                    }

                    @DisplayName("and order execution succeeds")
                    @Nested
                    class OrderServiceSucceeds {

                        @DisplayName("and update checker service throws an exception")
                        @Test
                        void updateCheckerServiceFails() {
                            when(updateCheckerService.start(order)).thenThrow(new UpdateFailedException());

                            assertThrows(FriendlySSLException.class, () -> service.orderCertificate(DOMAIN, login, domainKeyPair));
                        }

                        @DisplayName("and update checker service returns")
                        @Nested
                        class UpdateCheckerSucceeds {

                            @Mock
                            private ScheduledFuture updateCheckerFuture;

                            @BeforeEach
                            void setUp() {
                                when(updateCheckerService.start(order)).thenReturn(updateCheckerFuture);
                                when(config.getOrderTimeoutSeconds()).thenReturn(ORDER_TIMEOUT_SECONDS);
                            }

                            @DisplayName("and future retrieval fails")
                            @ParameterizedTest(name = "with exception {0}")
                            @ValueSource(classes = {InterruptedException.class, ExecutionException.class, CancellationException.class, TimeoutException.class})
                            void updateCheckerFutureFails(Class<Throwable> exceptionClass) throws ExecutionException, InterruptedException, TimeoutException {
                                when(updateCheckerFuture.get(ORDER_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                                        .thenThrow(exceptionClass);

                                assertThrows(FriendlySSLException.class, () -> service.orderCertificate(DOMAIN, login, domainKeyPair));
                            }

                            @DisplayName("and future retrieval succeeds")
                            @Nested
                            class UpdateCheckerRetrieved {

                                @DisplayName("with a null order certificate")
                                @Test
                                void nullCertificate() {
                                    when(order.getCertificate()).thenReturn(null);

                                    Optional<Certificate> certificate = service.orderCertificate(DOMAIN, login, domainKeyPair);

                                    assertEquals(Optional.empty(), certificate);
                                }

                                @DisplayName("with a non-null order certificate")
                                @Test
                                void nonNullCertificate() {
                                    Certificate orderCert = mock(Certificate.class);
                                    when(order.getCertificate()).thenReturn(orderCert);

                                    Optional<Certificate> certificate = service.orderCertificate(DOMAIN, login, domainKeyPair);

                                    assertEquals(Optional.of(orderCert), certificate);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}