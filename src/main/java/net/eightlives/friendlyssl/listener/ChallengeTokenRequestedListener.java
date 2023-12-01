package net.eightlives.friendlyssl.listener;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import net.eightlives.friendlyssl.exception.FriendlySSLException;
import net.eightlives.friendlyssl.exception.UpdateFailedException;
import net.eightlives.friendlyssl.service.ChallengeTokenStore;
import net.eightlives.friendlyssl.service.UpdateCheckerService;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * This listener listens for a {@link ChallengeTokenRequested} event an synchronously checks the challenge token
 * store for the event's token. If the token exists in the store, the future that the token is mapped to in this
 * instance is completed and removed from the map.
 */
@Component
public class ChallengeTokenRequestedListener implements ApplicationListener<ChallengeTokenRequested> {

    private static final Logger LOG = LoggerFactory.getLogger(ChallengeTokenRequestedListener.class);

    private final FriendlySSLConfig config;
    private final UpdateCheckerService updateCheckerService;
    private final ChallengeTokenStore challengeTokenStore;

    private final Map<String, CompletableFuture<Void>> tokensToListenerFutures = new HashMap<>();

    public ChallengeTokenRequestedListener(FriendlySSLConfig config,
                                           UpdateCheckerService updateCheckerService,
                                           ChallengeTokenStore challengeTokenStore) {
        this.config = config;
        this.updateCheckerService = updateCheckerService;
        this.challengeTokenStore = challengeTokenStore;
    }

    @Override
    public void onApplicationEvent(ChallengeTokenRequested event) {
        synchronized (tokensToListenerFutures) {
            if (challengeTokenStore.getTokens().containsKey(event.getToken())) {
                tokensToListenerFutures.remove(event.getToken()).complete(null);
            }
        }
    }

    /**
     * Trigger the given challenge and return a {@link CompletableFuture} that completes normally if the ACME challenge
     * endpoint is accessed within the configured timeout and then the given authorization update completes
     * successfully within the configured timeout.
     *
     * @param challenge     the ACME challenge to trigger
     * @param authorization the authorization to check for updates (this should contain the challenge)
     * @return a {@link CompletableFuture} that completes normally if the challenge is verified successfully by the
     * ACME server, and exceptionally if a timeout or exception occurs during this process
     * @throws FriendlySSLException if triggering the challenge causes an exception
     */
    public CompletableFuture<Void> getChallengeTokenVerification(Http01Challenge challenge, Authorization authorization) {
        challengeTokenStore.setToken(challenge.getToken(), challenge.getAuthorization());

        CompletableFuture<Void> listenerFuture = new CompletableFuture<>();
        tokensToListenerFutures.put(challenge.getToken(), listenerFuture);

        try {
            challenge.trigger();
        } catch (AcmeException e) {
            challengeTokenStore.getTokens().remove(challenge.getToken());
            throw new FriendlySSLException(e);
        }

        return listenerFuture.orTimeout(config.getTokenRequestedTimeoutSeconds(), TimeUnit.SECONDS)
                .thenRun(() -> {
                    try {
                        updateCheckerService.start(authorization)
                                .get(config.getAuthChallengeTimeoutSeconds(), TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        LOG.error("Timeout while checking for challenge status");
                        throw new FriendlySSLException(e);
                    } catch (InterruptedException | ExecutionException | CancellationException | UpdateFailedException e) {
                        throw new FriendlySSLException(e);
                    }
                })
                .whenComplete((aVoid, throwable) -> challengeTokenStore.getTokens().remove(challenge.getToken()));
    }
}
