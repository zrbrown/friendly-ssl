package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
        CompletableFuture<?>[] challenges = authorizations.stream()
                .filter(auth -> auth.getStatus() != Status.VALID)
                .map(this::processAuth)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(challenges);
    }

    private CompletableFuture<Void> processAuth(Authorization auth) {
        Http01Challenge challenge = auth.findChallenge(Http01Challenge.TYPE);
        if (challenge == null) {
            throw new SSLCertificateException(new IllegalStateException("HTTP Challenge does not exist"));
        }

        challengeTokenStore.setToken(challenge.getToken(), challenge.getAuthorization());
        CompletableFuture<ScheduledFuture<?>> challengeListener = challengeTokenRequestedListener.setTokenRequestedListener(
                challenge.getToken(),
                () -> updateCheckerService.start(auth)
        );

        try {
            challenge.trigger();
        } catch (AcmeException e) {
            throw new SSLCertificateException(e);
        }

        ScheduledFuture<?> updateChecker = getWithTimeout(
                challengeListener,
                config.getTokenRequestedTimeoutSeconds(),
                challenge.getToken(),
                "waiting for challenge endpoint to be hit");

        return CompletableFuture.runAsync(
                () -> getWithTimeout(updateChecker,
                        config.getAuthChallengeTimeoutSeconds(),
                        challenge.getToken(),
                        "checking for challenge status"));
    }

    private <T> T getWithTimeout(Future<T> future, int timeoutSeconds, String token, String operationDescription) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Timeout while " + operationDescription);
            throw new SSLCertificateException(e);
        } catch (InterruptedException | ExecutionException | CancellationException e) {
            throw new SSLCertificateException(e);
        } finally {
            challengeTokenStore.getTokens().remove(token);
        }
    }
}
