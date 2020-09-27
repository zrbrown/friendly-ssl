package net.eightlives.friendlyssl.controller;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.model.TermsOfServiceAgreeRequest;
import net.eightlives.friendlyssl.service.TermsOfServiceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.net.URI;

@Slf4j
@ConditionalOnExpression("'${friendly-ssl.endpoints-include}'.contains('tos')")
@RestController
@RequestMapping("/friendly-ssl/tos")
public class TermsOfServiceController {

    private final TermsOfServiceService termsOfServiceService;

    public TermsOfServiceController(TermsOfServiceService termsOfServiceService) {
        this.termsOfServiceService = termsOfServiceService;
    }

    @PostMapping(path = "/agree", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> agreeToTermsOfService(@Valid @RequestBody TermsOfServiceAgreeRequest termsOfServiceLink) {
        String termsLink = termsOfServiceLink.getTermsOfServiceLink();

        try {
            termsOfServiceService.writeTermsLink(URI.create(termsLink), true);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("URI could not be created from terms link '" + termsLink + "'");
        } catch (SSLCertificateException e) {
            log.error("Exception occurred while writing to terms of service file for terms link '" + termsLink + "'", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Exception occurred while writing to terms of service file for terms link '" + termsLink + "'");
        }
    }
}
