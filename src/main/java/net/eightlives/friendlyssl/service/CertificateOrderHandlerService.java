package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.KeyStoreGeneratorException;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

@Slf4j
@Component
public class CertificateOrderHandlerService {

    private final FriendlySSLConfig config;
    private final CertificateOrderService certificateOrderService;
    private final PKCS12KeyStoreService keyStoreService;

    public CertificateOrderHandlerService(FriendlySSLConfig config,
                                          CertificateOrderService certificateOrderService,
                                          PKCS12KeyStoreService keyStoreService) {
        this.config = config;
        this.certificateOrderService = certificateOrderService;
        this.keyStoreService = keyStoreService;
    }

    public Certificate handleCertificateOrder(Login login, KeyPair domainKeyPair) {
        return certificateOrderService.orderCertificate(config.getDomain(), login, domainKeyPair)
                .map(certificate -> {
                    try (OutputStream file = Files.newOutputStream(Path.of(config.getKeystoreFile()))) {
                        byte[] keyStore = keyStoreService.generateKeyStore(
                                certificate.getCertificateChain(),
                                domainKeyPair.getPrivate());
                        file.write(keyStore);
                    } catch (IOException | KeyStoreGeneratorException e) {
                        //TODO throw exception here?
                        log.error("Exception while creating keystore", e);
                    }

                    return certificate;
                }).orElseThrow(() -> new SSLCertificateException("Certificate was not returned"));
    }
}
