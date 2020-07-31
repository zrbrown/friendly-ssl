package net.eightlives.friendlyssl.controller;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import net.eightlives.friendlyssl.service.ChallengeTokenStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/.well-known/acme-challenge")
public class CertificateChallengeController {

    private final ChallengeTokenStore challengeTokenStore;
    private final ApplicationEventPublisher applicationEventPublisher;

    public CertificateChallengeController(ChallengeTokenStore challengeTokenStore,
                                          ApplicationEventPublisher applicationEventPublisher) {
        this.challengeTokenStore = challengeTokenStore;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @GetMapping(value = "/{token}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getToken(@PathVariable String token) {
        log.debug("Challenge endpoint hit for token: " + token);
        String content = challengeTokenStore.getTokens().getOrDefault(token, "");
        CompletableFuture.runAsync(
                () -> applicationEventPublisher.publishEvent(new ChallengeTokenRequested(this, token)));
        log.debug("Returning this content to the ACME server: " + content);
        return content;
    }
}
