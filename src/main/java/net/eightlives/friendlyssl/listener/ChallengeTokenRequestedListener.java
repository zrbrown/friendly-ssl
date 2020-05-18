package net.eightlives.friendlyssl.listener;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.service.ChallengeTokenStore;
import net.eightlives.friendlyssl.service.UpdateCheckerService;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
public class ChallengeTokenRequestedListener implements ApplicationListener<ChallengeTokenRequested> {

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

    //TODO rename
    public CompletableFuture<Void> getChallengeTokenVerification(Http01Challenge challenge, Authorization authorization) {
        challengeTokenStore.setToken(challenge.getToken(), challenge.getAuthorization());

        CompletableFuture<Void> listenerFuture = new CompletableFuture<>();
        tokensToListenerFutures.put(challenge.getToken(), listenerFuture);

        try {
            challenge.trigger();
        } catch (AcmeException e) {
            challengeTokenStore.getTokens().remove(challenge.getToken());
            throw new SSLCertificateException(e);
        }

        return listenerFuture.orTimeout(config.getTokenRequestedTimeoutSeconds(), TimeUnit.SECONDS)
                .thenRun(() -> {
                    try {
                        updateCheckerService.start(authorization)
                                .get(config.getAuthChallengeTimeoutSeconds(), TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        log.error("Timeout while checking for challenge status");
                        throw new SSLCertificateException(e);
                    } catch (InterruptedException | ExecutionException | CancellationException e) {
                        throw new SSLCertificateException(e);
                    }
                })
                .whenComplete((aVoid, throwable) -> challengeTokenStore.getTokens().remove(challenge.getToken()));
    }
}
