package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
public class CertificateCreateRenewService {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateCreateRenewService.class);

    private final FriendlySSLConfig config;
    private final AcmeAccountService accountService;
    private final PKCS12KeyStoreService keyStoreService;
    private final CertificateOrderHandlerService certificateOrderHandlerService;
    private final SSLContextService sslContextService;
    private final Clock clock;

    public CertificateCreateRenewService(FriendlySSLConfig config,
                                         AcmeAccountService accountService,
                                         PKCS12KeyStoreService keyStoreService,
                                         CertificateOrderHandlerService certificateOrderHandlerService,
                                         SSLContextService sslContextService,
                                         Clock clock) {
        this.config = config;
        this.accountService = accountService;
        this.keyStoreService = keyStoreService;
        this.certificateOrderHandlerService = certificateOrderHandlerService;
        this.sslContextService = sslContextService;
        this.clock = clock;
    }

    /**
     * Create and order a new certificate in the configured key store with the configured key alias.
     *
     * @return {@link CertificateRenewal} describing the result of the renewal and time at which the next renewal should
     * occur
     * @throws IllegalArgumentException if ACME session URL is invalid
     */
    public CertificateRenewal createCertificate() {
        LOG.info("Starting certificate create");

        return orderCertificate(KeyPairUtils.createKeyPair(2048));
    }

    /**
     * Renew the existing certificate in the configured key store with the configured key alias.
     *
     * @return {@link CertificateRenewal} describing the result of the renewal and time at which the next renewal should
     * occur
     * @throws IllegalArgumentException if ACME session URL is invalid
     */
    public CertificateRenewal renewCertificate() {
        LOG.info("Starting certificate renew");

        KeyPair domainKeyPair = keyStoreService.getKeyPair(config.getCertificateKeyAlias());

        return domainKeyPair == null ? createCertificate() : orderCertificate(domainKeyPair);
    }

    private CertificateRenewal orderCertificate(KeyPair domainKeyPair) {
        try {
            Session session = new Session(config.getAcmeSessionUrl());
            Login login = accountService.getOrCreateAccountLogin(session);
            LOG.info("Certificate account login accessed");

            LOG.info("Beginning certificate order.");
            Certificate certificate = certificateOrderHandlerService.handleCertificateOrder(login, domainKeyPair);
            Instant certificateExpiration = Instant.ofEpochMilli(certificate.getCertificate().getNotAfter().getTime());
            LOG.info("Certificate renewal successful. New certificate expiration time is " +
                    DateTimeFormatter.RFC_1123_DATE_TIME.format(certificateExpiration.atZone(ZoneOffset.UTC)));

            LOG.info("Reloading SSL context...");
            sslContextService.reloadSSLConfig();

            return new CertificateRenewal(CertificateRenewalStatus.SUCCESS,
                    certificateExpiration.minus(config.getAutoRenewalHoursBefore(), ChronoUnit.HOURS));
        } catch (IllegalArgumentException e) {
            LOG.error("acmeSessionUrl " + config.getAcmeSessionUrl() + " is invalid", e);
            throw e;
        } catch (Exception e) {
            LOG.error("Exception while ordering certificate, retry in " + config.getErrorRetryWaitHours() + " hours", e);
            return new CertificateRenewal(
                    CertificateRenewalStatus.ERROR,
                    clock.instant().plus(config.getErrorRetryWaitHours(), ChronoUnit.HOURS));
        }
    }
}
