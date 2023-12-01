package net.eightlives.friendlyssl.controller;

import net.eightlives.friendlyssl.exception.FriendlySSLException;
import net.eightlives.friendlyssl.model.TermsOfServiceAgreeRequest;
import net.eightlives.friendlyssl.service.TermsOfServiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@ConditionalOnExpression("'${friendly-ssl.endpoints-include}'.contains('tos')")
@RestController
@RequestMapping("/friendly-ssl/tos")
public class TermsOfServiceController {

    private static final Logger LOG = LoggerFactory.getLogger(TermsOfServiceController.class);

    private final TermsOfServiceService termsOfServiceService;

    public TermsOfServiceController(TermsOfServiceService termsOfServiceService) {
        this.termsOfServiceService = termsOfServiceService;
    }

    /**
     * Agree to the terms of service located at the given link.
     *
     * @param termsOfServiceLink the link to the terms of service being agreed to
     * @return <p>200 OK if terms were successfully agreed to</p>
     * <p>400 Bad Request if URI is malformed</p>
     * <p>500 Internal Server Error if an exception occurs</p>
     */
    @PostMapping(path = "/agree", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> agreeToTermsOfService(@Valid @RequestBody TermsOfServiceAgreeRequest termsOfServiceLink) {
        String termsLink = termsOfServiceLink.getTermsOfServiceLink();

        try {
            termsOfServiceService.writeTermsLink(URI.create(termsLink), true);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("URI could not be created from terms link '" + termsLink + "'");
        } catch (FriendlySSLException e) {
            LOG.error("Exception occurred while writing to terms of service file for terms link '" + termsLink + "'", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Exception occurred while writing to terms of service file for terms link '" + termsLink + "'");
        }
    }
}
