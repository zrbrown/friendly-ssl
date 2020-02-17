package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.KeyStoreGeneratorException;
import org.shredzone.acme4j.Login;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPair;

@Slf4j
@Component
public class CertificateOrderHandlerService {

    private final CertificateOrderService certificateOrderService;
    private final PKCS12KeyStoreService keyStoreGenerator;

    public CertificateOrderHandlerService(CertificateOrderService certificateOrderService, PKCS12KeyStoreService keyStoreGenerator) {
        this.certificateOrderService = certificateOrderService;
        this.keyStoreGenerator = keyStoreGenerator;
    }

    public void handleCertificateOrder(Login login, KeyPair domainKeyPair, boolean isRenewal) {
        certificateOrderService.orderCertificate("zackrbrown.com", login, domainKeyPair)
                .ifPresentOrElse(certificate -> {
                    if (!isRenewal) {
                        try (OutputStream file = new FileOutputStream("testout.p12")) {
                            byte[] keyStore = keyStoreGenerator.generateKeyStore(
                                    certificate.getCertificateChain(),
                                    domainKeyPair.getPrivate());
                            file.write(keyStore);
                        } catch (IOException | KeyStoreGeneratorException e) {
                            log.error("Exception while creating keystore", e);
                        }
                    }
                }, () -> log.error("Certificate was not returned"));
    }
}
