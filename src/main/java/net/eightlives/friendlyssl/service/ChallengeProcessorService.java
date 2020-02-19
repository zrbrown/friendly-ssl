package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.listener.ChallengeTokenRequestedListener;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.*;

@Component
public class ChallengeProcessorService {

    private final FriendlySSLConfig config;
    private final ChallengeTokenStore challengeTokenStore;
    private final ChallengeTokenRequestedListener challengeTokenRequestedListener;
    private final UpdateCheckerService updateCheckerService;

    public ChallengeProcessorService(FriendlySSLConfig config,
                                     ChallengeTokenStore challengeTokenStore,
                                     ChallengeTokenRequestedListener challengeTokenRequestedListener,
                                     UpdateCheckerService updateCheckerService) {
        this.config = config;
        this.challengeTokenStore = challengeTokenStore;
        this.challengeTokenRequestedListener = challengeTokenRequestedListener;
        this.updateCheckerService = updateCheckerService;
    }

    public CompletableFuture<?> process(List<Authorization> authorizations) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(authorizations.size());

        CompletableFuture<?>[] challenges = authorizations.stream()
                .filter(auth -> auth.getStatus() != Status.VALID)
                .map(auth -> processAuth(executorService, auth))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(challenges);
    }

    private CompletableFuture<Void> processAuth(ScheduledExecutorService executorService, Authorization auth) {
        Http01Challenge challenge = auth.findChallenge(Http01Challenge.TYPE);
        if (challenge == null) {
            throw new SSLCertificateException(new IllegalStateException("HTTP Challenge does not exist"));
        }

        challengeTokenStore.setToken(challenge.getToken(), challenge.getAuthorization());
        CompletableFuture<ScheduledFuture<?>> listener = challengeTokenRequestedListener.setTokenRequestedListener(
                challenge.getToken(),
                () -> updateCheckerService.start(executorService, auth)
        );

        try {
            challenge.trigger();
        } catch (AcmeException e) {
            throw new SSLCertificateException(e);
        }

        ScheduledFuture<?> updateChecker = getWithTimeout(listener, config.getTokenRequestedTimeoutSeconds(), challenge.getToken());

        return CompletableFuture.runAsync(() -> getWithTimeout(updateChecker, config.getAuthChallengeTimeoutSeconds(), challenge.getToken()));
    }

    private <T> T getWithTimeout(Future<T> future, int timeoutSeconds, String token) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException | CancellationException e) {
            throw new SSLCertificateException(e);
        } finally {
            challengeTokenStore.getTokens().remove(token);
        }
    }
}
