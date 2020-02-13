package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.exception.AcmeException;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.util.Optional;
import java.util.concurrent.*;

@Component
public class CertificateOrderService {

    private final ChallengeProcessorService challengeProcessorService;
    private final CSRService csrService;
    private final UpdateCheckerService updateCheckerService;

    public CertificateOrderService(ChallengeProcessorService challengeProcessorService,
                                   CSRService csrService,
                                   UpdateCheckerService updateCheckerService) {
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
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            updateCheckerService.start(executor, order).get(30, TimeUnit.SECONDS);

            executor.shutdown();

            return Optional.ofNullable(order.getCertificate());
        } catch (AcmeException | InterruptedException | ExecutionException | TimeoutException | CancellationException e) {
            throw new SSLCertificateException(e);
        }
    }
}
