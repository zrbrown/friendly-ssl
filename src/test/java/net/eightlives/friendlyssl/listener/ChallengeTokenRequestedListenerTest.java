package net.eightlives.friendlyssl.listener;

import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@Execution(ExecutionMode.CONCURRENT)
class ChallengeTokenRequestedListenerTest {

    private ChallengeTokenRequestedListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChallengeTokenRequestedListener();
    }

    @DisplayName("Testing onApplicationEvent without the token being found should not throw an exception")
    @Test
    void onApplicationEventWithNotFoundToken() {
        listener.onApplicationEvent(new ChallengeTokenRequested(this, "token"));
    }

    @DisplayName("Testing setTokenRequestedListener return value")
    @Test
    void setTokenRequestedListener() throws ExecutionException, InterruptedException {
        ScheduledFuture<Void> scheduledFuture = mock(ScheduledFuture.class);

        CompletableFuture<ScheduledFuture<Void>> future = listener.setTokenRequestedListener("token", () -> scheduledFuture);
        future.complete(scheduledFuture);

        assertEquals(scheduledFuture, future.get());
    }

    @DisplayName("Testing that onApplicationEvent should complete the future from setTokenRequestedListener")
    @Test
    void setTokenRequestedListenerAndOnApplicationEvent() {
        ScheduledFuture<Void> scheduledFuture = mock(ScheduledFuture.class);

        CompletableFuture<ScheduledFuture<Void>> future = listener.setTokenRequestedListener("token", () -> scheduledFuture);
        listener.onApplicationEvent(new ChallengeTokenRequested(this, "token"));

        assertTrue(future.isDone());
    }

    @DisplayName("Testing that onApplicationEvent multiple times should not throw an exception")
    @Test
    void setTokenRequestedListenerAndOnApplicationEventMultiple() {
        ScheduledFuture<Void> scheduledFuture = mock(ScheduledFuture.class);

        CompletableFuture<ScheduledFuture<Void>> future = listener.setTokenRequestedListener("token", () -> scheduledFuture);
        listener.onApplicationEvent(new ChallengeTokenRequested(this, "token"));
        listener.onApplicationEvent(new ChallengeTokenRequested(this, "token"));

        assertTrue(future.isDone());
    }
}