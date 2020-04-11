package net.eightlives.friendlyssl.controller;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/friendly-ssl/certificate")
public class CertificateController {

    private final SSLCertificateCreateRenewService createRenewService;

    public CertificateController(SSLCertificateCreateRenewService createRenewService) {
        this.createRenewService = createRenewService;
    }

    public ResponseEntity<CertificateRenewal> order() {
        CertificateRenewal certificateRenewal = createRenewService.createOrRenew();

        return switch (certificateRenewal.status()) {
            case ALREADY_VALID, SUCCESS -> ResponseEntity.ok(certificateRenewal);
            case ERROR -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        };
    }
}
