package net.eightlives.friendlyssl.listener;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import net.eightlives.friendlyssl.exception.FriendlySSLException;
import net.eightlives.friendlyssl.service.ChallengeTokenStore;
import net.eightlives.friendlyssl.service.UpdateCheckerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeTokenRequestedListenerTest {

    private static final String CHALLENGE_TOKEN = "this is a token";
    private static final String CHALLENGE_AUTH = "this is an authorization";

    private ChallengeTokenRequestedListener listener;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private UpdateCheckerService updateCheckerService;
    @Mock
    private ChallengeTokenStore challengeTokenStore;
    @Mock
    private Http01Challenge challenge;
    @Mock
    private Authorization auth;

    @BeforeEach
    void setUp() {
        listener = new ChallengeTokenRequestedListener(config, updateCheckerService, challengeTokenStore);
    }

    @DisplayName("onApplicationEvent without the token being found should not throw an exception")
    @Test
    void onApplicationEventWithNotFoundToken() {
        listener.onApplicationEvent(new ChallengeTokenRequested(this, "token"));
    }

    @DisplayName("Test getChallengeTokenVerification")
    @Nested
    class GetChallengeTokenVerification {

        private final Map<String, String> tokensToContent = new HashMap<>();

        @BeforeEach
        void setUp() {
            when(challenge.getToken()).thenReturn(CHALLENGE_TOKEN);
            when(challenge.getAuthorization()).thenReturn(CHALLENGE_AUTH);
            when(challengeTokenStore.getTokens()).thenReturn(tokensToContent);
            doAnswer(invocation -> tokensToContent.put(CHALLENGE_TOKEN, CHALLENGE_AUTH))
                    .when(challengeTokenStore).setToken(CHALLENGE_TOKEN, CHALLENGE_AUTH);
        }

        @DisplayName("when challenge trigger causes exception")
        @Test
        void challengeTriggerException() throws AcmeException {
            doThrow(new AcmeException()).when(challenge).trigger();

            assertThrows(FriendlySSLException.class,
                    () -> listener.getChallengeTokenVerification(challenge, auth));
        }

        @DisplayName("when challenge trigger does not cause exception")
        @Tag("slow")
        @Nested
        class ChallengeTriggerPasses {

            @BeforeEach
            void setUp() {
                when(config.getTokenRequestedTimeoutSeconds()).thenReturn(1);
            }

            @DisplayName("when onApplicationEvent is not called within token-requested-timeout-seconds")
            @Test
            void listenerFutureTimeout() {
                CompletableFuture<Void> future = listener.getChallengeTokenVerification(challenge, auth);

                assertThrows(ExecutionException.class, future::get);
            }

            @DisplayName("when onApplicationEvent is called within token-requested-timeout-seconds")
            @Nested
            class ListenerFutureSuccess {

                @Mock
                private ScheduledFuture<Void> updateCheckerResult;

                @BeforeEach
                void setUp() {
                    when(updateCheckerService.start(auth)).thenReturn(updateCheckerResult);
                    when(config.getAuthChallengeTimeoutSeconds()).thenReturn(1);
                }

                @DisplayName("and update checker fails")
                @ParameterizedTest(name = "with exception {0}")
                @ValueSource(classes = {TimeoutException.class, InterruptedException.class, ExecutionException.class, CancellationException.class})
                void updateCheckerException(Class<Throwable> exceptionClass) throws InterruptedException, ExecutionException, TimeoutException {
                    when(updateCheckerResult.get(1, TimeUnit.SECONDS)).thenThrow(exceptionClass);

                    CompletableFuture<Void> future = listener.getChallengeTokenVerification(challenge, auth);
                    listener.onApplicationEvent(new ChallengeTokenRequested(this, CHALLENGE_TOKEN));

                    ExecutionException exception = assertThrows(ExecutionException.class, future::get);
                    assertInstanceOf(FriendlySSLException.class, exception.getCause());
                }

                @DisplayName("and update checker completes")
                @Test
                void updateCheckerCompletes() throws InterruptedException, ExecutionException, TimeoutException {
                    when(updateCheckerResult.get(1, TimeUnit.SECONDS)).thenReturn(null);

                    CompletableFuture<Void> future = listener.getChallengeTokenVerification(challenge, auth);
                    listener.onApplicationEvent(new ChallengeTokenRequested(this, CHALLENGE_TOKEN));

                    assertTrue(future.isDone());
                }
            }
        }

        @AfterEach
        void tearDown() throws AcmeException {
            assertFalse(tokensToContent.containsKey(CHALLENGE_TOKEN));

            InOrder inOrder = inOrder(challengeTokenStore, challenge);

            inOrder.verify(challengeTokenStore).setToken(CHALLENGE_TOKEN, CHALLENGE_AUTH);
            inOrder.verify(challenge).trigger();
        }
    }
}
