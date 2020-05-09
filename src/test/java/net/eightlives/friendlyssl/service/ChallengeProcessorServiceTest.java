package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.listener.ChallengeTokenRequestedListener;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.AcmeJsonResource;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeProcessorServiceTest {

    private static final String CHALLENGE_TOKEN = "this is a token";
    private static final String CHALLENGE_AUTH = "this is an authorization";

    private ChallengeProcessorService service;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private ChallengeTokenStore challengeTokenStore;
    @Mock
    private ChallengeTokenRequestedListener challengeTokenRequestedListener;
    @Mock
    private UpdateCheckerService updateCheckerService;

    @BeforeEach
    void setUp() {
        service = new ChallengeProcessorService(config, challengeTokenStore,
                challengeTokenRequestedListener, updateCheckerService);
    }

    @DisplayName("Valid authorizations should be filtered out")
    @Test
    void validAuth() {
        Authorization auth = mock(Authorization.class);
        when(auth.getStatus()).thenReturn(Status.VALID);

        CompletableFuture<Void> responseFuture = service.process(List.of(auth));

        assertTrue(responseFuture.isDone());
    }

    @DisplayName("When authorizations are invalid")
    @Nested
    class ValidAuthorization {

        @Mock
        private Authorization auth;
        @Mock
        private Http01Challenge challenge;

        @BeforeEach
        void setUp() {
            when(auth.getStatus()).thenReturn(Status.PENDING);
            when(auth.findChallenge(Http01Challenge.TYPE)).thenReturn(challenge);
        }

        @DisplayName("and no HTTP challenge found for just one authorization, an exception should be thrown")
        @Test
        void noHttpChallengeException() {
            Authorization noHttpAuth = mock(Authorization.class);
            when(noHttpAuth.getStatus()).thenReturn(Status.PENDING);
            when(noHttpAuth.findChallenge(Http01Challenge.TYPE)).thenReturn(null);

            assertThrows(SSLCertificateException.class, () -> service.process(List.of(auth, noHttpAuth)));
        }

        @DisplayName("and all authorizations have HTTP challenges")
        @Nested
        class HttpChallenges {

            @BeforeEach
            void setUp() {
                when(challenge.getToken()).thenReturn(CHALLENGE_TOKEN);
                when(challenge.getAuthorization()).thenReturn(CHALLENGE_AUTH);
            }

            @DisplayName("when challenge trigger throws exception")
            @Test
            void triggerException() throws AcmeException {
                doThrow(new AcmeException()).when(challenge).trigger();

                assertThrows(SSLCertificateException.class, () -> service.process(List.of(auth)));
            }

            @DisplayName("when trigger succeeds")
            @Nested
            class TriggerSucceeds {

                @Mock
                private CompletableFuture<ScheduledFuture<Void>> challengeListener;

                private final Map<String, String> tokenStoreTokens = new HashMap<>(Map.of(CHALLENGE_TOKEN, CHALLENGE_AUTH));

                @BeforeEach
                void setUp() {
                    when(config.getTokenRequestedTimeoutSeconds()).thenReturn(3);
                    when(challengeTokenRequestedListener.setTokenRequestedListener(matches(CHALLENGE_TOKEN), any()))
                            .thenReturn(challengeListener);
                    when(challengeTokenStore.getTokens()).thenReturn(tokenStoreTokens);
                }

                @DisplayName("and challenge listener fails")
                @ParameterizedTest(name = "with exception {0}")
                @ValueSource(classes = {TimeoutException.class, InterruptedException.class, ExecutionException.class, CancellationException.class})
                void challengeListenerFails(Class<Throwable> exceptionClass) throws ExecutionException, InterruptedException, TimeoutException {
                    when(challengeListener.get(3, TimeUnit.SECONDS)).thenThrow(exceptionClass);

                    assertThrows(SSLCertificateException.class, () -> service.process(List.of(auth)));
                }

                @DisplayName("and challenge listener succeeds")
                @Nested
                class ChallengeListenerSucceeds {

                    @Mock
                    private ScheduledFuture<Void> updateChecker;

                    @BeforeEach
                    void setUp() throws InterruptedException, ExecutionException, TimeoutException {
                        when(challengeListener.get(3, TimeUnit.SECONDS)).thenReturn(updateChecker);
                        when(config.getAuthChallengeTimeoutSeconds()).thenReturn(2);
                    }

                    @DisplayName("and update checker fails")
                    @ParameterizedTest(name = "with exception {0}")
                    @ValueSource(classes = {TimeoutException.class, InterruptedException.class, ExecutionException.class, CancellationException.class})
                    @Timeout(value = 2)
                    void updateCheckerFails(Class<Throwable> exceptionClass) throws InterruptedException, ExecutionException, TimeoutException {
                        when(updateChecker.get(2, TimeUnit.SECONDS)).thenThrow(exceptionClass);

                        CompletableFuture<Void> responseFuture = service.process(List.of(auth));

                        CountDownLatch latch = new CountDownLatch(1);
                        responseFuture.handle((aVoid, throwable) -> {
                            assertTrue(throwable.getCause() instanceof SSLCertificateException);
                            return Void.TYPE;
                        }).thenRun(latch::countDown);
                        latch.await();
                    }

                    @DisplayName("and update checker succeeds")
                    @Test
                    void updateCheckerSucceeds() throws InterruptedException {
                        CompletableFuture<Void> responseFuture = service.process(List.of(auth));

                        CountDownLatch latch = new CountDownLatch(1);
                        responseFuture.thenRun(latch::countDown);
                        latch.await();

                        assertTrue(responseFuture.isDone());
                    }
                }

                @AfterEach
                void tearDown() {
                    assertFalse(tokenStoreTokens.containsKey(CHALLENGE_TOKEN));
                }
            }

            @AfterEach
            void tearDown() throws AcmeException {
                InOrder inOrder = inOrder(challengeTokenStore, challengeTokenRequestedListener, challenge);

                inOrder.verify(challengeTokenStore, times(1)).setToken(CHALLENGE_TOKEN, CHALLENGE_AUTH);

                ArgumentCaptor<Supplier<ScheduledFuture<Void>>> listenerArg = ArgumentCaptor.forClass(Supplier.class);
                inOrder.verify(challengeTokenRequestedListener, times(1)).setTokenRequestedListener(
                        matches(CHALLENGE_TOKEN), listenerArg.capture()
                );
                ScheduledFuture<Void> updateFuture = mock(ScheduledFuture.class);
                when(updateCheckerService.start(any(AcmeJsonResource.class))).thenReturn(updateFuture);
                listenerArg.getValue().get();
                verify(updateCheckerService, times(1)).start(auth);

                inOrder.verify(challenge, times(1)).trigger();
            }
        }
    }
}