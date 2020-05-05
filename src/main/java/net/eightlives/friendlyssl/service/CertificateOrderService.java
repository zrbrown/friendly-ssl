package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.exception.UpdateFailedException;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.exception.AcmeException;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class CertificateOrderService {

    private final FriendlySSLConfig config;
    private final ChallengeProcessorService challengeProcessorService;
    private final CSRService csrService;
    private final UpdateCheckerService updateCheckerService;

    public CertificateOrderService(FriendlySSLConfig config,
                                   ChallengeProcessorService challengeProcessorService,
                                   CSRService csrService,
                                   UpdateCheckerService updateCheckerService) {
        this.config = config;
        this.challengeProcessorService = challengeProcessorService;
        this.csrService = csrService;
        this.updateCheckerService = updateCheckerService;
    }

    public Optional<Certificate> orderCertificate(String domain, Login login, KeyPair domainKeyPair) {
        try {
            Order order = login.getAccount()
                    .newOrder()
                    .domain(domain)
                    .create();

            challengeProcessorService.process(order.getAuthorizations()).get();
            byte[] csr = csrService.generateCSR(domain, domainKeyPair);
            order.execute(csr);

            updateCheckerService.start(order).get(config.getOrderTimeoutSeconds(), TimeUnit.SECONDS);

            return Optional.ofNullable(order.getCertificate());
        } catch (AcmeException | InterruptedException | ExecutionException | TimeoutException
                | CancellationException | UpdateFailedException e) {
            throw new SSLCertificateException(e);
        }
    }
}
