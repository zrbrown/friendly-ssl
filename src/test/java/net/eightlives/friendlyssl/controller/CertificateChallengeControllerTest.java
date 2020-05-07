package net.eightlives.friendlyssl.controller;

import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import net.eightlives.friendlyssl.junit.UUIDStringProvider;
import net.eightlives.friendlyssl.service.ChallengeTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(MockitoExtension.class)
class CertificateChallengeControllerTest {

    private CertificateChallengeController controller;

    @Mock
    private ChallengeTokenStore mockStore;
    @Mock
    private ApplicationEventPublisher mockEventPublisher;

    @BeforeEach
    void setUp() {
        controller = new CertificateChallengeController(mockStore, mockEventPublisher);
    }

    @Nested
    @DisplayName("Testing getToken")
    class GetToken {
        @DisplayName("with no existing token")
        @ParameterizedTest(name = "for token {0}")
        @ArgumentsSource(UUIDStringProvider.class)
        void getTokenWithNoExistingToken(String token) throws InterruptedException {
            Mockito.when(mockStore.getTokens()).thenReturn(Collections.emptyMap());
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockEventPublisher).publishEvent(any(ChallengeTokenRequested.class));

            String response = controller.getToken(token);
            assertEquals("", response);
            latch.await();

            ArgumentCaptor<ChallengeTokenRequested> challengeTokenRequestedArg = ArgumentCaptor.forClass(ChallengeTokenRequested.class);
            verify(mockEventPublisher, times(1)).publishEvent(challengeTokenRequestedArg.capture());
            ChallengeTokenRequested challengeTokenRequested = challengeTokenRequestedArg.getValue();
            assertEquals(token, challengeTokenRequested.getToken());
        }

        @DisplayName("with existing token")
        @ParameterizedTest(name = "for token {0}")
        @ArgumentsSource(UUIDStringProvider.class)
        void getTokenWithExistingToken(String token) throws InterruptedException {
            Mockito.when(mockStore.getTokens()).thenReturn(Map.of(token, "stuff"));
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockEventPublisher).publishEvent(any(ChallengeTokenRequested.class));

            String response = controller.getToken(token);
            assertEquals("stuff", response);
            latch.await();

            ArgumentCaptor<ChallengeTokenRequested> challengeTokenRequestedArg = ArgumentCaptor.forClass(ChallengeTokenRequested.class);
            verify(mockEventPublisher, times(1)).publishEvent(challengeTokenRequestedArg.capture());
            ChallengeTokenRequested challengeTokenRequested = challengeTokenRequestedArg.getValue();
            assertEquals(token, challengeTokenRequested.getToken());
        }
    }
}