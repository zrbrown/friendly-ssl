package net.eightlives.friendlyssl.controller;

import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import net.eightlives.friendlyssl.junit.UUIDStringProvider;
import net.eightlives.friendlyssl.service.ChallengeTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("controller")
@Tag("slow")
@ExtendWith(MockitoExtension.class)
class CertificateChallengeControllerTest {

    @Import(CertificateChallengeController.class)
    @SpringBootApplication
    static class TestApp {
    }

    private MockMvc mvc;

    @Mock
    private ChallengeTokenStore mockStore;
    @Mock
    private ApplicationEventPublisher mockEventPublisher;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new CertificateChallengeController(mockStore, mockEventPublisher))
                .build();
    }

    @Nested
    @DisplayName("Testing getToken")
    @Execution(ExecutionMode.SAME_THREAD)
    class GetToken {

        @DisplayName("with no existing token")
        @ParameterizedTest(name = "for token {0}")
        @ArgumentsSource(UUIDStringProvider.class)
        void getTokenWithNoExistingToken(String token) throws Exception {
            Mockito.when(mockStore.getTokens()).thenReturn(Collections.emptyMap());
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockEventPublisher).publishEvent(any(ChallengeTokenRequested.class));

            mvc.perform(get("/.well-known/acme-challenge/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertTrue(result.getResponse().getContentAsString().isBlank()))
                    .andReturn();
            latch.await();

            ArgumentCaptor<ChallengeTokenRequested> challengeTokenRequestedArg = ArgumentCaptor.forClass(ChallengeTokenRequested.class);
            verify(mockEventPublisher, times(1)).publishEvent(challengeTokenRequestedArg.capture());
            ChallengeTokenRequested challengeTokenRequested = challengeTokenRequestedArg.getValue();
            assertEquals(token, challengeTokenRequested.getToken());
        }

        @DisplayName("with existing token")
        @ParameterizedTest(name = "for token {0}")
        @ArgumentsSource(UUIDStringProvider.class)
        void getTokenWithExistingToken(String token) throws Exception {
            Mockito.when(mockStore.getTokens()).thenReturn(Map.of(token, "stuff"));
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockEventPublisher).publishEvent(any(ChallengeTokenRequested.class));

            mvc.perform(get("/.well-known/acme-challenge/{token}", token))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertEquals("stuff", result.getResponse().getContentAsString()));
            latch.await();

            ArgumentCaptor<ChallengeTokenRequested> challengeTokenRequestedArg = ArgumentCaptor.forClass(ChallengeTokenRequested.class);
            verify(mockEventPublisher, times(1)).publishEvent(challengeTokenRequestedArg.capture());
            ChallengeTokenRequested challengeTokenRequested = challengeTokenRequestedArg.getValue();
            assertEquals(token, challengeTokenRequested.getToken());
        }
    }
}
