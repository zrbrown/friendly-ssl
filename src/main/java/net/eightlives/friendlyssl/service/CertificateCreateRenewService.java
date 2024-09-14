package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.FriendlySSLException;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.ssl.SslProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.server.Ssl;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Component
public class CertificateCreateRenewService {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateCreateRenewService.class);

    private final FriendlySSLConfig config;
    private final ServerProperties serverConfig;
    private final SslProperties sslConfig;
    private final AcmeAccountService accountService;
    private final PKCS12KeyStoreService keyStoreService;
    private final CertificateOrderHandlerService certificateOrderHandlerService;
    private final Clock clock;
    private final SslBundles sslBundles;

    public CertificateCreateRenewService(FriendlySSLConfig config,
                                         ServerProperties serverConfig,
                                         SslProperties sslConfig,
                                         AcmeAccountService accountService,
                                         PKCS12KeyStoreService keyStoreService,
                                         CertificateOrderHandlerService certificateOrderHandlerService,
                                         Clock clock,
                                         SslBundles sslBundles) {
        this.config = config;
        this.serverConfig = serverConfig;
        this.sslConfig = sslConfig;
        this.accountService = accountService;
        this.keyStoreService = keyStoreService;
        this.certificateOrderHandlerService = certificateOrderHandlerService;
        this.clock = clock;
        this.sslBundles = sslBundles;
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

            Ssl ssl = serverConfig.getSsl();
            if (ssl == null) {
                throw new FriendlySSLException("SSL is not configured by server.ssl");
            }
            String bundle = ssl.getBundle();
            if (bundle == null) {
                throw new FriendlySSLException("SSL bundle name is not configured by server.ssl.bundle");
            }
            CountDownLatch reloadLatch = new CountDownLatch(1);
            sslBundles.addBundleUpdateHandler(bundle, _ -> {
                LOG.info("Finished reloading SSL context");
                reloadLatch.countDown();
            });

            LOG.info("Beginning certificate order.");
            Certificate certificate = certificateOrderHandlerService.handleCertificateOrder(login, domainKeyPair);
            Instant certificateExpiration = Instant.ofEpochMilli(certificate.getCertificate().getNotAfter().getTime());
            LOG.info("Certificate renewal successful. New certificate expiration time is {}",
                    DateTimeFormatter.RFC_1123_DATE_TIME.format(certificateExpiration.atZone(ZoneOffset.UTC)));

            LOG.info("Reloading SSL context...");
            SslProperties.Bundles sslBundle = sslConfig.getBundle();
            if (sslBundle == null) {
                throw new FriendlySSLException("Spring SSL Bundle is not configured by spring.ssl.bundle");
            }
            Duration quietPeriod = sslBundle.getWatch().getFile().getQuietPeriod();
            if (!reloadLatch.await(quietPeriod.toSeconds() + 1, TimeUnit.SECONDS)) {
                throw new FriendlySSLException("SSL certificate was not reloaded within the time set by spring.ssl.bundle.watch.file.quiet-period (" + quietPeriod.toSeconds() + " seconds)");
            }

            return new CertificateRenewal(CertificateRenewalStatus.SUCCESS,
                    certificateExpiration.minus(config.getAutoRenewalHoursBefore(), ChronoUnit.HOURS));
        } catch (IllegalArgumentException e) {
            LOG.error("acmeSessionUrl {} is invalid", config.getAcmeSessionUrl(), e);
            throw e;
        } catch (Exception e) {
            LOG.error("Exception while ordering certificate, retry in {} hours", config.getErrorRetryWaitHours(), e);
            return new CertificateRenewal(
                    CertificateRenewalStatus.ERROR,
                    clock.instant().plus(config.getErrorRetryWaitHours(), ChronoUnit.HOURS));
        }
    }
}
