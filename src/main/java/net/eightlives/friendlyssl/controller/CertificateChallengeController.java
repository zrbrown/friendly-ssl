package net.eightlives.friendlyssl.controller;

import net.eightlives.friendlyssl.event.ChallengeTokenRequested;
import net.eightlives.friendlyssl.service.ChallengeTokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/.well-known/acme-challenge")
public class CertificateChallengeController {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateChallengeController.class);

    private final ChallengeTokenStore challengeTokenStore;
    private final ApplicationEventPublisher applicationEventPublisher;

    public CertificateChallengeController(ChallengeTokenStore challengeTokenStore,
                                          ApplicationEventPublisher applicationEventPublisher) {
        this.challengeTokenStore = challengeTokenStore;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Answer an ACME challenge with the string of content associated with the given token, which the server should
     * have been informed of before this endpoint is accessed.
     *
     * @param token the token for which to return associated content
     * @return the content associated with the given token, or empty string if not content was found
     */
    @GetMapping(value = "/{token}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getToken(@PathVariable String token) {
        LOG.debug("Challenge endpoint hit for token: " + token);
        String content = challengeTokenStore.getTokens().getOrDefault(token, "");
        CompletableFuture.runAsync(
                () -> applicationEventPublisher.publishEvent(new ChallengeTokenRequested(this, token)));
        LOG.debug("Returning this content to the ACME server: " + content);
        return content;
    }
}
