package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Component
public class SSLCertificateCreateRenewService {

    private final FriendlySSLConfig config;
    private final AcmeAccountService accountService;
    private final PKCS12KeyStoreService keyStoreService;
    private final CertificateOrderHandlerService certificateOrderHandlerService;
    private final Clock clock;

    public SSLCertificateCreateRenewService(FriendlySSLConfig config,
                                            AcmeAccountService accountService,
                                            PKCS12KeyStoreService keyStoreService,
                                            CertificateOrderHandlerService certificateOrderHandlerService,
                                            Clock clock) {
        this.config = config;
        this.accountService = accountService;
        this.keyStoreService = keyStoreService;
        this.certificateOrderHandlerService = certificateOrderHandlerService;
        this.clock = clock;
    }

    public CertificateRenewal createOrRenew() {
        try {
            log.info("Starting certificate create/renew");
            Session session = new Session(config.getAcmeSessionUrl());
            Login login = accountService.getOrCreateAccountLogin(session);
            log.info("Certificate account login accessed");

            Optional<X509Certificate> existingCertificate = keyStoreService.getCertificate(config.getCertificateFriendlyName());
            if (existingCertificate.isPresent()) {
                Instant renewTime = Instant.ofEpochMilli(existingCertificate.get().getNotAfter().getTime());
                if (clock.instant().plus(config.getAutoRenewalHoursBefore(), ChronoUnit.HOURS).isBefore(renewTime)) {
                    log.info("Existing certificate expiration time is " + renewTime);
                    return new CertificateRenewal(
                            CertificateRenewalStatus.ALREADY_VALID,
                            renewTime);
                }
            }

            KeyPair domainKeyPair = existingCertificate.map(
                    certificate -> keyStoreService.getKeyPair(certificate, config.getCertificateFriendlyName()))
                    .orElse(null);
            boolean isRenewal = domainKeyPair != null;
            domainKeyPair = domainKeyPair == null ? KeyPairUtils.createKeyPair(2048) : domainKeyPair;

            log.info("Beginning certificate order. Renewal: " + isRenewal);
            Certificate certificate = certificateOrderHandlerService.handleCertificateOrder(login, domainKeyPair, isRenewal);
            log.info("Certificate renewal successful. New certificate expiration time is " + certificate.getCertificate().getNotAfter());
            return new CertificateRenewal(
                    CertificateRenewalStatus.SUCCESS,
                    Instant.ofEpochMilli(certificate.getCertificate().getNotAfter().getTime()));
        } catch (IllegalArgumentException e) {
            log.error("acmeSessionUrl " + config.getAcmeSessionUrl() + " is invalid", e);
            throw e;
        } catch (Exception e) {
            log.error("Exception while ordering certificate, retry in " + config.getErrorRetryWaitHours() + " hours", e);
            return new CertificateRenewal(
                    CertificateRenewalStatus.ERROR,
                    clock.instant().plus(config.getErrorRetryWaitHours(), ChronoUnit.HOURS));
        }
    }
}
