package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    /**
     * Start auto-renewal. An existing certificate with configured key alias will be checked for expiration before
     * renewing.
     *
     * @return {@link CertificateRenewal} containing the renewal status and the next time that auto-renewal should be run
     */
    public CertificateRenewal autoRenew() {
        log.info("Auto-renew starting...");
        return keyStoreService.getCertificate(config.getCertificateFriendlyName()).map(certificate -> {
            Instant renewTime = Instant.ofEpochMilli(certificate.getNotAfter().getTime());
            log.info("Existing certificate expiration time is " +
                    DateTimeFormatter.RFC_1123_DATE_TIME.format(renewTime.atZone(ZoneOffset.UTC)));
            if (clock.instant().plus(config.getAutoRenewalHoursBefore(), ChronoUnit.HOURS).isBefore(renewTime)) {
                return new CertificateRenewal(
                        CertificateRenewalStatus.ALREADY_VALID,
                        renewTime.minus(config.getAutoRenewalHoursBefore(), ChronoUnit.HOURS));
            } else {
                return createRenewService.createOrRenew(certificate);
            }
        }).orElseGet(() -> createRenewService.createOrRenew(null));
    }
}
