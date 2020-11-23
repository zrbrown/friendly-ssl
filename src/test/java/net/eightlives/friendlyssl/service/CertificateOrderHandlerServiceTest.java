package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.KeyStoreGeneratorException;
import net.eightlives.friendlyssl.exception.FriendlySSLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CertificateOrderHandlerServiceTest {

    private CertificateOrderHandlerService service;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private CertificateOrderService certificateOrderService;
    @Mock
    private PKCS12KeyStoreService keyStoreService;
    @Mock
    private Login login;
    @Mock
    private Certificate certificate;

    private KeyPair domainKeyPair;

    @BeforeEach
    void setUp() throws IOException {
        when(config.getDomain()).thenReturn("domain.com");
        domainKeyPair = KeyPairUtils.readKeyPair(Files.newBufferedReader(
                Path.of("src", "test", "resources", "keypair.pem")));
        service = new CertificateOrderHandlerService(config, certificateOrderService, keyStoreService);
    }

    @DisplayName("CertificateOrderService throws an exception")
    @Test
    void certificateOrderServiceThrowsException() {
        when(certificateOrderService.orderCertificate("domain.com", login, domainKeyPair))
                .thenThrow(new FriendlySSLException(""));

        assertThrows(FriendlySSLException.class, () -> service.handleCertificateOrder(login, domainKeyPair));
    }

    @DisplayName("CertificateOrderService does not return a certificate")
    @Test
    void certificateOrderServiceCertificateNotFound() {
        when(certificateOrderService.orderCertificate("domain.com", login, domainKeyPair))
                .thenReturn(Optional.empty());

        assertThrows(FriendlySSLException.class, () -> service.handleCertificateOrder(login, domainKeyPair));
    }

    @DisplayName("When CertificateOrderService returns a certificate")
    @Nested
    class CertificateOrderServiceSucceeds {

        private List<X509Certificate> certChain = Collections.emptyList();
        private Path keystoreFile;

        @BeforeEach
        void setUp(@TempDir Path temp) throws IOException {
            domainKeyPair = KeyPairUtils.readKeyPair(Files.newBufferedReader(
                    Path.of("src", "test", "resources", "keypair.pem")));
            when(certificateOrderService.orderCertificate("domain.com", login, domainKeyPair))
                    .thenReturn(Optional.of(certificate));
            keystoreFile = temp.resolve("not_exists");

            when(config.getKeystoreFile()).thenReturn(keystoreFile.toString());
            when(certificate.getCertificateChain()).thenReturn(certChain);
        }

        @DisplayName("and KeyStoreGeneratorException is thrown")
        @Test
        void keystoreGeneratorException() {
            when(keyStoreService.generateKeyStore(certChain, domainKeyPair.getPrivate()))
                    .thenThrow(new KeyStoreGeneratorException(new RuntimeException()));

            assertThrows(FriendlySSLException.class, () -> service.handleCertificateOrder(login, domainKeyPair));
        }

        @DisplayName("then key store file is generated and written")
        @Test
        void keyStoreFileWritten() {
            when(keyStoreService.generateKeyStore(certChain, domainKeyPair.getPrivate()))
                    .thenReturn("this is a certificate".getBytes());

            Certificate cert = service.handleCertificateOrder(login, domainKeyPair);
            assertSame(certificate, cert);

            verify(keyStoreService, times(1))
                    .generateKeyStore(certChain, domainKeyPair.getPrivate());

            assertTrue(Files.exists(keystoreFile));
        }
    }
}
