package net.eightlives.friendlyssl.startup;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.KeyStoreGeneratorException;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.service.AcmeAccountService;
import net.eightlives.friendlyssl.service.CertificateOrderService;
import net.eightlives.friendlyssl.service.PKCS12KeyStoreGeneratorService;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPair;

@Slf4j
@Component
public class SSLStartupRunner implements CommandLineRunner {

    private AcmeAccountService accountService;
    private CertificateOrderService certificateOrderService;
    private PKCS12KeyStoreGeneratorService keyStoreGenerator;

    public SSLStartupRunner(AcmeAccountService accountService,
                            CertificateOrderService certificateOrderService,
                            PKCS12KeyStoreGeneratorService keyStoreGenerator) {
        this.accountService = accountService;
        this.certificateOrderService = certificateOrderService;
        this.keyStoreGenerator = keyStoreGenerator;
    }

    @Override
    public void run(String... args) {
        Session session = new Session("acme://letsencrypt.org/staging");
        Login login = accountService.getOrCreateAccountLogin(session);
        KeyPair domainKeyPair = KeyPairUtils.createKeyPair(2048);

        try {
            certificateOrderService.orderCertificate("zackrbrown.com", login, domainKeyPair)
                    .ifPresent(certificate -> {
                        try (OutputStream file = new FileOutputStream("testout.p12")) {
                            byte[] keyStore = keyStoreGenerator.generateKeyStore(
                                    certificate.getCertificateChain(),
                                    domainKeyPair.getPrivate());
                            file.write(keyStore);
                        } catch (IOException | KeyStoreGeneratorException e) {
                            log.error("Exception while creating keystore", e);
                        }
                    });
        } catch (SSLCertificateException e) {
            log.error("Exception while ordering certificate", e);
        }
    }
}
