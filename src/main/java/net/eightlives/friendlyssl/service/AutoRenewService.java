package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public class AutoRenewService {

    private final FriendlySSLConfig config;
    private final SSLCertificateCreateRenewService createRenewService;
    private final PKCS12KeyStoreService keyStoreService;
    private final Clock clock;

    public AutoRenewService(FriendlySSLConfig config,
                            SSLCertificateCreateRenewService createRenewService,
                            PKCS12KeyStoreService keyStoreService,
                            Clock clock) {
        this.config = config;
        this.createRenewService = createRenewService;
        this.keyStoreService = keyStoreService;
        this.clock = clock;
    }

    public CertificateRenewal autoRenew() {
        return keyStoreService.getCertificate(config.getCertificateFriendlyName()).map(certificate -> {
            Instant renewTime = Instant.ofEpochMilli(certificate.getNotAfter().getTime());
            if (clock.instant().plus(config.getAutoRenewalHoursBefore(), ChronoUnit.HOURS).isBefore(renewTime)) {
                log.info("Existing certificate expiration time is " + renewTime);
                return new CertificateRenewal(
                        CertificateRenewalStatus.ALREADY_VALID,
                        renewTime.minus(config.getAutoRenewalHoursBefore(), ChronoUnit.HOURS));
            } else {
                return createRenewService.createOrRenew(certificate);
            }
        }).orElseGet(() -> createRenewService.createOrRenew(null));
    }
}
