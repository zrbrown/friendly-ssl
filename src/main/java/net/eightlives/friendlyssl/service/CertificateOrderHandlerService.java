package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
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

    private final FriendlySSLConfig config;
    private final CertificateOrderService certificateOrderService;
    private final PKCS12KeyStoreService keyStoreGenerator;

    public CertificateOrderHandlerService(FriendlySSLConfig config,
                                          CertificateOrderService certificateOrderService,
                                          PKCS12KeyStoreService keyStoreGenerator) {
        this.config = config;
        this.certificateOrderService = certificateOrderService;
        this.keyStoreGenerator = keyStoreGenerator;
    }

    public void handleCertificateOrder(Login login, KeyPair domainKeyPair, boolean isRenewal) {
        certificateOrderService.orderCertificate(config.getAccountEmail(), login, domainKeyPair)
                .ifPresentOrElse(certificate -> {
                    if (!isRenewal) {
                        try (OutputStream file = new FileOutputStream(config.getKeystoreFile())) {
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
