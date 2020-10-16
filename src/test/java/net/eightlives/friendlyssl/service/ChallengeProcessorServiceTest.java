package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.listener.ChallengeTokenRequestedListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Http01Challenge;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeProcessorServiceTest {

    private ChallengeProcessorService service;

    @Mock
    private ChallengeTokenRequestedListener challengeTokenRequestedListener;

    @BeforeEach
    void setUp() {
        service = new ChallengeProcessorService(challengeTokenRequestedListener);
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

        @DisplayName("and no HTTP challenge found for any authorization, an exception should be thrown")
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

            @DisplayName("when challengeTokenRequestedListener throws exception")
            @Test
            void listenerException() {
                when(challengeTokenRequestedListener.getChallengeTokenVerification(challenge, auth))
                        .thenThrow(new SSLCertificateException(""));

                assertThrows(SSLCertificateException.class, () -> service.process(List.of(auth)));
            }

            @DisplayName("when all processed authorizations complete successfully")
            @Test
            void futuresComplete() {
                when(challengeTokenRequestedListener.getChallengeTokenVerification(challenge, auth))
                        .thenReturn(CompletableFuture.completedFuture(null));

                assertTrue(service.process(List.of(auth)).isDone());
            }

            @DisplayName("when trigger succeeds")
            @Nested
            class TriggerSucceeds {

                private final CompletableFuture<Void> challengeFuture = new CompletableFuture<>();

                @BeforeEach
                void setUp() {
                    when(challengeTokenRequestedListener.getChallengeTokenVerification(challenge, auth))
                            .thenReturn(challengeFuture);
                }

                @DisplayName("and challenge future fails")
                @Test
                void challengeFutureFails() {
                    challengeFuture.completeExceptionally(new SSLCertificateException(""));

                    Exception e = assertThrows(ExecutionException.class, () -> service.process(List.of(auth)).get(1, TimeUnit.SECONDS));
                    assertTrue(e.getCause() instanceof SSLCertificateException);
                }

                @DisplayName("and challenge future times out")
                @Test
                void challengeFutureTimeout() {
                    challengeFuture.completeExceptionally(new TimeoutException());

                    Exception e = assertThrows(ExecutionException.class, () -> service.process(List.of(auth)).get(1, TimeUnit.SECONDS));
                    assertTrue(e.getCause() instanceof TimeoutException);
                }

                @DisplayName("and challenge future succeeds")
                @Test
                void challengeFutureSucceeds() throws ExecutionException, InterruptedException, TimeoutException {
                    challengeFuture.complete(null);

                    CompletableFuture<Void> future = service.process(List.of(auth));
                    assertTrue(future.isDone());
                }
            }
        }
    }
}
