package net.eightlives.friendlyssl.controller;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.service.TermsOfServiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/friendly-ssl/tos")
public class TermsOfServiceController {

    private final FriendlySSLConfig config;
    private final TermsOfServiceService termsOfServiceService;

    public TermsOfServiceController(FriendlySSLConfig config,
                                    TermsOfServiceService termsOfServiceService) {
        this.config = config;
        this.termsOfServiceService = termsOfServiceService;
    }

    @GetMapping("/{termsLink}/agree")
    public ResponseEntity<String> agreeToTermsOfService(@PathVariable String termsLink) {
        try {
            termsOfServiceService.writeTermsLink(URI.create(termsLink), true);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("URI could not be created from terms link " + termsLink);
        }
    }
}
