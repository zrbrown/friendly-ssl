package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.KeyStoreGeneratorException;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@Execution(ExecutionMode.CONCURRENT)
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
                Paths.get("src", "test", "resources", "keypair.pem")));
        service = new CertificateOrderHandlerService(config, certificateOrderService, keyStoreService);
    }

    @DisplayName("CertificateOrderService throws an exception")
    @Test
    void certificateOrderServiceThrowsException() {
        when(certificateOrderService.orderCertificate("domain.com", login, domainKeyPair))
                .thenThrow(new SSLCertificateException(new RuntimeException()));

        assertThrows(SSLCertificateException.class, () -> service.handleCertificateOrder(login, domainKeyPair, false));
    }

    @DisplayName("CertificateOrderService does not return a certificate")
    @Test
    void certificateOrderServiceCertificateNotFound() {
        when(certificateOrderService.orderCertificate("domain.com", login, domainKeyPair))
                .thenReturn(Optional.empty());

        assertThrows(SSLCertificateException.class, () -> service.handleCertificateOrder(login, domainKeyPair, false));
    }

    @DisplayName("When CertificateOrderService returns a certificate")
    @Nested
    class CertificateOrderServiceSucceeds {

        @BeforeEach
        void setUp() throws IOException {
            domainKeyPair = KeyPairUtils.readKeyPair(Files.newBufferedReader(
                    Paths.get("src", "test", "resources", "keypair.pem")));
            when(certificateOrderService.orderCertificate("domain.com", login, domainKeyPair))
                    .thenReturn(Optional.of(certificate));
        }

        @DisplayName("and this is a renewal, the ordered certificate should be returned")
        @Test
        void isRenewal() {
            Certificate cert = service.handleCertificateOrder(login, domainKeyPair, true);

            assertSame(certificate, cert);
        }

        @DisplayName("and this is not a renewal")
        @Nested
        class NoRenewal {

            private List<X509Certificate> certChain = Collections.emptyList();

            @BeforeEach
            void setUp() {
                when(config.getKeystoreFile()).thenReturn(
                        Paths.get("src", "test", "resources", "keystore.p12").toString());
                when(certificate.getCertificateChain()).thenReturn(certChain);
            }

            @DisplayName("and KeyStoreGeneratorException is thrown")
            @Test
            void keystoreGeneratorException() {
                when(keyStoreService.generateKeyStore(certChain, domainKeyPair.getPrivate()))
                        .thenThrow(new KeyStoreGeneratorException(new RuntimeException()));

                Certificate cert = service.handleCertificateOrder(login, domainKeyPair, false);
                assertSame(certificate, cert);
            }

            @DisplayName("then key store file is generated and written")
            @Test
            void keyStoreFileWritten() {
                when(keyStoreService.generateKeyStore(certChain, domainKeyPair.getPrivate()))
                        .thenReturn("this is a certificate".getBytes());

                Certificate cert = service.handleCertificateOrder(login, domainKeyPair, false);
                assertSame(certificate, cert);

                verify(keyStoreService, times(1))
                        .generateKeyStore(certChain, domainKeyPair.getPrivate());

                Files.exists(Paths.get("src", "test", "resources", "keystore.p12"));
            }

            @AfterEach
            void tearDown() throws IOException {
                Files.deleteIfExists(Paths.get("src", "test", "resources", "keystore.p12"));
            }
        }
    }
}