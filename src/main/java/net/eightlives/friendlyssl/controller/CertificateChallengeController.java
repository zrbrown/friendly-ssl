package net.eightlives.friendlyssl.controller;

import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import net.eightlives.friendlyssl.service.ChallengeTokenStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
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
        applicationEventPublisher.publishEvent(new ChallengeTokenRequested(this, token));
        return challengeTokenStore.getTokens().getOrDefault(token, "");
    }
}
