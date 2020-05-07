package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Execution(ExecutionMode.CONCURRENT)
@ExtendWith(MockitoExtension.class)
class PKCS12KeyStoreServiceTest {

    private static final String PK_FRIENDLY_NAME = "JUnit";

    private PKCS12KeyStoreService service;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private LocalIdGeneratorService localIdGeneratorService;

    @BeforeEach
    void setUp() {
        service = new PKCS12KeyStoreService(config, localIdGeneratorService);
    }

    @DisplayName("Test generateKeyStore")
    @Nested
    class GenerateKeyStore {

        private List<X509Certificate> certificateChain;
        private PrivateKey privateKey;

        @BeforeEach
        void setUp() throws CertificateException, IOException {
            when(localIdGeneratorService.generate()).thenReturn("abcdef".getBytes());
            when(config.getCertificateFriendlyName()).thenReturn(PK_FRIENDLY_NAME);

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            certificateChain = new ArrayList<>((Collection<? extends X509Certificate>)
                    certificateFactory.generateCertificates(Files.newInputStream(
                            Paths.get("src", "test", "resources", "certificate_chain.pem")
                    )));

            privateKey = KeyPairUtils.readKeyPair(Files.newBufferedReader(
                    Paths.get("src", "test", "resources", "keypair.pem"))).getPrivate();
        }

        @DisplayName("Generated keystore should match snapshot in relevant areas")
        @RepeatedTest(value = 50)
        void generateKeystore() throws IOException {
            byte[] snapshot = Files.readAllBytes(
                    Paths.get("src", "test", "resources", "existing_keystore.p12"));
            byte[] keystore = service.generateKeyStore(certificateChain, privateKey);

            assertEquals(snapshot.length, keystore.length);

            assertArrayEquals(
                    Arrays.copyOfRange(snapshot, 0, 93),
                    Arrays.copyOfRange(keystore, 0, 93));
            assertArrayEquals(
                    Arrays.copyOfRange(snapshot, 113, 120),
                    Arrays.copyOfRange(keystore, 113, 120));
            assertArrayEquals(
                    Arrays.copyOfRange(snapshot, 2233, 2302),
                    Arrays.copyOfRange(keystore, 2233, 2302));
            assertArrayEquals(
                    Arrays.copyOfRange(snapshot, 2323, 2330),
                    Arrays.copyOfRange(keystore, 2323, 2330));
            assertArrayEquals(
                    Arrays.copyOfRange(snapshot, 3555, 3623),
                    Arrays.copyOfRange(keystore, 3555, 3623));
            assertArrayEquals(
                    Arrays.copyOfRange(snapshot, 3644, 3645),
                    Arrays.copyOfRange(keystore, 3644, 3645));
            assertArrayEquals(
                    Arrays.copyOfRange(snapshot, 3666, 3669),
                    Arrays.copyOfRange(keystore, 3666, 3669));
        }
    }

    @DisplayName("Test getKeyPair")
    @Nested
    class GetKeyPair {

        private Certificate certificate;

        @BeforeEach
        void setUp() throws IOException, CertificateException {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            certificate = certificateFactory.generateCertificate(Files.newInputStream(
                    Paths.get("src", "test", "resources", "certificate_chain.pem")));
        }

        @DisplayName("when key store file does not exist")
        @Test
        void keyStoreNotFound() {
            when(config.getKeystoreFile()).thenReturn(
                    Paths.get("src", "test", "resources", "notreal.p12").toString());

            assertNull(service.getKeyPair(certificate, PK_FRIENDLY_NAME));
        }

        @DisplayName("when key store file is invalid")
        @Test
        void invalidKeyStore() {
            when(config.getKeystoreFile()).thenReturn(
                    Paths.get("src", "test", "resources", "invalid.p12").toString());

            assertNull(service.getKeyPair(certificate, PK_FRIENDLY_NAME));
        }

        @DisplayName("when key store is valid")
        @Nested
        class KeyStoreValid {

            @BeforeEach
            void setUp() {
                when(config.getKeystoreFile()).thenReturn(
                        Paths.get("src", "test", "resources", "existing_keystore.p12").toString());
            }

            @DisplayName("when private key friendly name is not in the key store")
            @Test
            void friendlyNameKeyNotFound() {
                assertNull(service.getKeyPair(certificate, "NotFound"));
            }

            @DisplayName("when key pair is successfully generated")
            @Test
            void generationSuccessful() {
                KeyPair keyPair = service.getKeyPair(certificate, PK_FRIENDLY_NAME);

                assertEquals(certificate.getPublicKey(), keyPair.getPublic());
            }
        }
    }

    @DisplayName("Test getCertificate")
    @Nested
    class GetCertificate {

        @BeforeEach
        void setUp() {
            when(config.getKeystoreFile()).thenReturn(
                    Paths.get("src", "test", "resources", "existing_keystore.p12").toString());
        }

        @DisplayName("when key store file does not exist")
        @Test
        void keyStoreNotFound() {
            when(config.getKeystoreFile()).thenReturn(
                    Paths.get("src", "test", "resources", "notreal.p12").toString());

            assertEquals(Optional.empty(), service.getCertificate(PK_FRIENDLY_NAME));
        }

        @DisplayName("when key store file is invalid")
        @Test
        void invalidKeyStore() {
            when(config.getKeystoreFile()).thenReturn(
                    Paths.get("src", "test", "resources", "invalid.p12").toString());

            assertEquals(Optional.empty(), service.getCertificate(PK_FRIENDLY_NAME));
        }

        @DisplayName("when key store is valid")
        @Nested
        class KeyStoreValid {

            @DisplayName("when private key friendly name is not in the key store")
            @Test
            void friendlyNameKeyNotFound() throws CertificateException, IOException {
                when(config.getKeystoreFile()).thenReturn(
                        Paths.get("src", "test", "resources", "existing_keystore.p12").toString());

                assertEquals(Optional.empty(), service.getCertificate("NotFound"));
            }

            @DisplayName("when certificate is an X.509 certificate")
            @Test
            void validKeyStore() throws CertificateException, IOException {
                when(config.getKeystoreFile()).thenReturn(
                        Paths.get("src", "test", "resources", "existing_keystore.p12").toString());

                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                Certificate certificate = certificateFactory.generateCertificate(Files.newInputStream(
                        Paths.get("src", "test", "resources", "certificate_chain.pem")));

                assertEquals(Optional.of(certificate), service.getCertificate(PK_FRIENDLY_NAME));
            }
        }
    }
}