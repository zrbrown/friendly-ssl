package net.eightlives.friendlyssl.controller;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.service.PKCS12KeyStoreService;
import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.X509Certificate;

@Slf4j
@ConditionalOnExpression("'${friendly-ssl.endpoints-include}'.contains('certificate')")
@RestController
@RequestMapping("/friendly-ssl/certificate")
public class CertificateController {

    private final FriendlySSLConfig config;
    private final SSLCertificateCreateRenewService createRenewService;
    private final PKCS12KeyStoreService keyStoreService;

    public CertificateController(FriendlySSLConfig config, SSLCertificateCreateRenewService createRenewService,
                                 PKCS12KeyStoreService keyStoreService) {
        this.config = config;
        this.createRenewService = createRenewService;
        this.keyStoreService = keyStoreService;
    }

    @GetMapping(path = "/order", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CertificateRenewal> order() {
        X509Certificate existingCertificate = keyStoreService.getCertificate(config.getCertificateFriendlyName())
                .orElse(null);

        CertificateRenewal certificateRenewal = createRenewService.createOrRenew(existingCertificate);

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
