package net.eightlives.friendlyssl.listener;

import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

@Component
public class ChallengeTokenRequestedListener implements ApplicationListener<ChallengeTokenRequested> {

    private final Map<String, Supplier<ScheduledFuture<?>>> tokensToListeners = new HashMap<>();
    private final Map<String, CompletableFuture<ScheduledFuture<?>>> tokensToFutures = new HashMap<>();

    @Override
    public void onApplicationEvent(ChallengeTokenRequested event) {
        synchronized (tokensToListeners) {
            if (tokensToListeners.containsKey(event.getToken())) {
                ScheduledFuture<?> listener = tokensToListeners.remove(event.getToken()).get();
                tokensToFutures.remove(event.getToken()).complete(listener);
            }
        }
    }

    public CompletableFuture<ScheduledFuture<?>> setTokenRequestedListener(String token, Supplier<ScheduledFuture<?>> listener) {
        tokensToListeners.put(token, listener);

        CompletableFuture<ScheduledFuture<?>> future = new CompletableFuture<>();
        tokensToFutures.put(token, future);

        return future;
    }
}
