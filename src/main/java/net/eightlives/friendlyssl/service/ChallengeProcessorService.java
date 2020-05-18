package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.listener.ChallengeTokenRequestedListener;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChallengeProcessorService {

    private final ChallengeTokenRequestedListener challengeTokenRequestedListener;

    public ChallengeProcessorService(ChallengeTokenRequestedListener challengeTokenRequestedListener) {
        this.challengeTokenRequestedListener = challengeTokenRequestedListener;
    }

    public CompletableFuture<Void> process(List<Authorization> authorizations) {
        CompletableFuture<?>[] challenges = authorizations.stream()
                .filter(auth -> auth.getStatus() != Status.VALID)
                .map(auth -> {
                    Http01Challenge challenge = auth.findChallenge(Http01Challenge.TYPE);
                    if (challenge == null) {
                        throw new SSLCertificateException(new IllegalStateException("HTTP Challenge does not exist"));
                    }
                    return new AuthorizationAndChallenge(auth, challenge);
                })
                .collect(Collectors.toList()).stream()
                .map(this::processAuth)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(challenges);
    }

    private static final class AuthorizationAndChallenge {
        public final Authorization authorization;
        public final Http01Challenge challenge;

        private AuthorizationAndChallenge(Authorization authorization, Http01Challenge challenge) {
            this.authorization = authorization;
            this.challenge = challenge;
        }
    }

    private CompletableFuture<Void> processAuth(AuthorizationAndChallenge authAndChallenge) {
        return challengeTokenRequestedListener.getChallengeTokenVerification(
                authAndChallenge.challenge, authAndChallenge.authorization);
    }
}
