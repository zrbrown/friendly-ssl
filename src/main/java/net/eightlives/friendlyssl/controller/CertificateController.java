package net.eightlives.friendlyssl.controller;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.service.CertificateCreateRenewService;
import net.eightlives.friendlyssl.service.PKCS12KeyStoreService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@ConditionalOnExpression("'${friendly-ssl.endpoints-include}'.contains('certificate')")
@RestController
@RequestMapping("/friendly-ssl/certificate")
public class CertificateController {

    private final FriendlySSLConfig config;
    private final CertificateCreateRenewService createRenewService;
    private final PKCS12KeyStoreService keyStoreService;

    public CertificateController(FriendlySSLConfig config, CertificateCreateRenewService createRenewService,
                                 PKCS12KeyStoreService keyStoreService) {
        this.config = config;
        this.createRenewService = createRenewService;
        this.keyStoreService = keyStoreService;
    }

    /**
     * Order a certificate manually. This might be done if the user wants to order early or if there was a previous
     * failure to order (such as when terms of service haven't been agreed to) and the user does not want to wait for
     * the retry.
     *
     * @return <p>200 OK if certificate was ordered successfully</p>
     * <p>500 Internal Server Error if an exception occurs</p>
     */
    @GetMapping(path = "/order", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CertificateRenewal> order() {
        boolean existingCertificate = keyStoreService.getCertificate(config.getCertificateKeyAlias()).isPresent();

        CertificateRenewal certificateRenewal = existingCertificate ?
                createRenewService.renewCertificate() :
                createRenewService.createCertificate();

        switch (certificateRenewal.getStatus()) {
            case ALREADY_VALID:
            case SUCCESS:
                return ResponseEntity.ok(certificateRenewal);
            case ERROR:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            default:
                log.error("Unknown CertificateRenewal " + certificateRenewal.getStatus().name() + ". This is most likely a build problem.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
