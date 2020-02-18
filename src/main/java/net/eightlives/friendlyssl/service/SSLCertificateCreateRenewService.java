package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
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

    public SSLCertificateCreateRenewService(FriendlySSLConfig config,
                                            AcmeAccountService accountService,
                                            PKCS12KeyStoreService keyStoreService,
                                            CertificateOrderHandlerService certificateOrderHandlerService) {
        this.config = config;
        this.accountService = accountService;
        this.keyStoreService = keyStoreService;
        this.certificateOrderHandlerService = certificateOrderHandlerService;
    }

    public Instant createOrRenew() {
        try {
            Session session = new Session(config.getAcmeSessionUrl());
            Login login = accountService.getOrCreateAccountLogin(session);

            Optional<X509Certificate> existingCertificate = keyStoreService.getCertificate(config.getCertificateFriendlyName());
            if (existingCertificate.isPresent()) {
                Instant renewTime = (Instant.ofEpochMilli(existingCertificate.get().getNotAfter().getTime()));
                if (Instant.now().plus(config.getAutoRenewalHoursBefore(), ChronoUnit.HOURS).isBefore(renewTime)) {
                    return renewTime;
                }
            }

            KeyPair domainKeyPair = existingCertificate.map(
                    certificate -> keyStoreService.getKeyPair(certificate, config.getCertificateFriendlyName()))
                    .orElse(null);
            boolean isRenewal = domainKeyPair != null;
            domainKeyPair = domainKeyPair == null ? KeyPairUtils.createKeyPair(2048) : domainKeyPair;

            certificateOrderHandlerService.handleCertificateOrder(login, domainKeyPair, isRenewal);
            return Instant.now(); // TODO needs to return the new cert renewal time
        } catch (SSLCertificateException e) {
            log.error("Exception while ordering certificate, retry in " + config.getErrorRetryWaitHours() + " hours", e);
            return Instant.now().plus(config.getErrorRetryWaitHours(), ChronoUnit.HOURS);
        }
    }
}
