package net.eightlives.friendlyssl.listener;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
public class KeystoreCheckListenerTest {

    private KeystoreCheckListener listener;

    @Mock
    private ConfigurableEnvironment environment;

    private final Path notExists = Path.of("not_exists.p12");

    @BeforeEach
    void setUp() {
        listener = new KeystoreCheckListener(null, null);

        when(environment.getProperty("friendly-ssl.keystore-file")).thenReturn(notExists.toString());
        when(environment.getProperty("friendly-ssl.domain")).thenReturn("test.me");
        when(environment.getProperty("friendly-ssl.certificate-friendly-name")).thenReturn("friendlyssl");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(notExists);
    }

    @DisplayName("When a required property ")
    @ParameterizedTest(name = "{0} has not been set")
    @ValueSource(strings = {"friendly-ssl.keystore-file", "friendly-ssl.certificate-friendly-name", "friendly-ssl.domain"})
    void requiredPropertyMissing(String property) {
        when(environment.getProperty(property)).thenReturn(null);

        listener.environmentPrepared(environment);

        assertTrue(Files.notExists(notExists));
    }

    @DisplayName("When an existing certificate is not present")
    @Test
    void certificateNotExists(@TempDir Path temp) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        Path notExists = temp.resolve("not_exists.p12");
        when(environment.getProperty("friendly-ssl.keystore-file")).thenReturn(notExists.toString());

        listener.environmentPrepared(environment);

        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(Files.newInputStream(notExists), "".toCharArray());
        X509Certificate certificate = (X509Certificate) store.getCertificate("friendlyssl");

        assertTrue(Instant.ofEpochMilli(certificate.getNotAfter().getTime())
                .isBefore(Instant.now().plus(1, ChronoUnit.DAYS)));
        assertEquals("DC=NET, DC=EIGHTLIVES, DC=FRIENDLYSSL, CN=test.me",
                certificate.getIssuerDN().getName());
        assertEquals("DC=NET, DC=EIGHTLIVES, DC=FRIENDLYSSL, CN=test.me",
                certificate.getSubjectDN().getName());
    }

    @DisplayName("When an existing certificate is not present and keystore path has no parent directory")
    @Test
    void certificateNotExistsNoParent() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        when(environment.getProperty("friendly-ssl.keystore-file")).thenReturn(notExists.toString());

        listener.environmentPrepared(environment);

        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(Files.newInputStream(notExists), "".toCharArray());
        X509Certificate certificate = (X509Certificate) store.getCertificate("friendlyssl");

        assertTrue(Instant.ofEpochMilli(certificate.getNotAfter().getTime())
                .isBefore(Instant.now().plus(1, ChronoUnit.DAYS)));
        assertEquals("DC=NET, DC=EIGHTLIVES, DC=FRIENDLYSSL, CN=test.me",
                certificate.getIssuerDN().getName());
        assertEquals("DC=NET, DC=EIGHTLIVES, DC=FRIENDLYSSL, CN=test.me",
                certificate.getSubjectDN().getName());
    }

    @DisplayName("When existing certificate has a password")
    @ExtendWith(OutputCaptureExtension.class)
    @Test
    void certificatePassword(CapturedOutput output) throws IOException {
        when(environment.getProperty("friendly-ssl.keystore-file")).thenReturn("src/test/resources/password_keystore.p12");

        Path keystorePath = Path.of("src/test/resources/password_keystore.p12");
        byte[] keystore = Files.readAllBytes(keystorePath);

        listener.environmentPrepared(environment);

        assertArrayEquals(keystore, Files.readAllBytes(keystorePath));
    }

    @DisplayName("When an existing certificate is present")
    @Nested
    class CertificateExists {

        Path keystorePath;
        byte[] keystore;

        @BeforeEach
        void setUp() throws IOException {
            when(environment.getProperty("friendly-ssl.keystore-file")).thenReturn("src/test/resources/existing_keystore.p12");

            keystorePath = Path.of("src/test/resources/existing_keystore.p12");
            keystore = Files.readAllBytes(keystorePath);
        }

        @AfterEach
        void tearDown() throws IOException {
            Files.newOutputStream(keystorePath).write(keystore);
        }

        @DisplayName("When friendly name is correct")
        @Test
        void certificateExists() throws IOException {
            listener.environmentPrepared(environment);

            assertArrayEquals(keystore, Files.readAllBytes(keystorePath));
        }

        @DisplayName("When friendly name is incorrect")
        @Test
        void certificateNameIncorrect() throws IOException {
            when(environment.getProperty("friendly-ssl.certificate-friendly-name")).thenReturn("certificateNameIncorrect");

            listener.environmentPrepared(environment);

            byte[] newKeystore = Files.readAllBytes(keystorePath);

            if (keystore.length == newKeystore.length) {
                for (int i = 0; i < newKeystore.length; i++) {
                    if (newKeystore[i] != keystore[i]) {
                        break;
                    }
                    if (i == newKeystore.length - 1) {
                        fail("Keystore should have changed");
                    }
                }
            }
        }
    }
}
