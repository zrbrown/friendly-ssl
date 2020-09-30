package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.factory.AccountBuilderFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Problem;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeServerException;
import org.shredzone.acme4j.exception.AcmeUserActionRequiredException;
import org.shredzone.acme4j.toolbox.JSON;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcmeAccountServiceTest {

    private AcmeAccountService service;
    private AcmeServerException accountDoesNotExistException;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private TermsOfServiceService termsOfServiceService;
    @Mock
    private AccountBuilderFactory accountBuilderFactory;
    @Mock
    private Session session;

    @BeforeEach
    void setUp() throws MalformedURLException {
        service = new AcmeAccountService(config, termsOfServiceService, accountBuilderFactory);
        accountDoesNotExistException = new AcmeServerException(new Problem(
                JSON.parse("{\"type\":\"urn:ietf:params:acme:error:accountDoesNotExist\"}"),
                new URL("http://localhost")));
    }

    @DisplayName("getting TOS link throws SSLCertificateException")
    @Test
    void getTermsOfServiceLinkException() {
        when(termsOfServiceService.getTermsOfServiceLink(session)).thenThrow(
                new SSLCertificateException(""));

        assertThrows(SSLCertificateException.class, () -> service.getOrCreateAccountLogin(session));
    }

    @DisplayName("When TOS link is valid")
    @Nested
    class ValidTOSLink {

        private final URI TERMS_OF_SERVICE_LINK = URI.create("http://localhost:8000");

        @Mock
        private Login login;
        @Mock
        private AccountBuilder accountBuilder;

        private KeyPair accountKeyPair;

        @BeforeEach
        void setUp() throws IOException {
            accountKeyPair = KeyPairUtils.readKeyPair(Files.newBufferedReader(
                    Path.of("src", "test", "resources", "keypair.pem")));
            when(termsOfServiceService.getTermsOfServiceLink(session)).thenReturn(TERMS_OF_SERVICE_LINK);

            when(accountBuilderFactory.accountBuilder()).thenReturn(accountBuilder);
            when(accountBuilder.useKeyPair(any(KeyPair.class))).thenReturn(accountBuilder);
            when(accountBuilder.onlyExisting()).thenReturn(accountBuilder);
        }

        @DisplayName("when account key value file exists, it is used")
        @Nested
        class AccountKeyValueFileExists {

            private final String accountFile = Path.of("src", "test", "resources", "keypair.pem").toString();

            @BeforeEach
            void setUp() {
                when(config.getAccountPrivateKeyFile()).thenReturn(accountFile);
            }

            @DisplayName("when account creation succeeds")
            @Test
            void accountCreationSuccessful() throws AcmeException {
                when(accountBuilder.createLogin(session)).thenReturn(login);

                Login responseLogin = service.getOrCreateAccountLogin(session);
                assertEquals(login, responseLogin);

                ArgumentCaptor<KeyPair> keyPairArg = ArgumentCaptor.forClass(KeyPair.class);
                verify(accountBuilder, times(1)).useKeyPair(keyPairArg.capture());
                KeyPair keyPair = keyPairArg.getValue();
                assertEquals(accountKeyPair.getPublic(), keyPair.getPublic());
                assertEquals(accountKeyPair.getPrivate(), keyPair.getPrivate());
            }

            @DisplayName("when account doesn't exist, but TOS accepted")
            @Test
            void accountNotExistsTOSAccepted() throws AcmeException, MalformedURLException {
                when(accountBuilder.createLogin(session))
                        .thenThrow(accountDoesNotExistException)
                        .thenReturn(login);
                when(accountBuilder.addEmail("test@test.com")).thenReturn(accountBuilder);
                when(accountBuilder.agreeToTermsOfService()).thenReturn(accountBuilder);
                when(termsOfServiceService.termsAccepted(TERMS_OF_SERVICE_LINK)).thenReturn(true);
                when(config.getAccountEmail()).thenReturn("test@test.com");

                Login responseLogin = service.getOrCreateAccountLogin(session);
                assertEquals(login, responseLogin);

                ArgumentCaptor<KeyPair> keyPairArg = ArgumentCaptor.forClass(KeyPair.class);
                verify(accountBuilder, times(2)).useKeyPair(keyPairArg.capture());
                KeyPair keyPair = keyPairArg.getValue();
                assertEquals(accountKeyPair.getPublic(), keyPair.getPublic());
                assertEquals(accountKeyPair.getPrivate(), keyPair.getPrivate());
            }
        }

        @DisplayName("when account creation succeeds")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"keypair.pem", "non-existing.pem"})
        void accountCreationSuccessful(String accountFile) throws AcmeException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Path.of("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session)).thenReturn(login);

            Login responseLogin = service.getOrCreateAccountLogin(session);
            assertEquals(login, responseLogin);

            verify(accountBuilder, times(1)).onlyExisting();
            verify(accountBuilder, times(1)).createLogin(session);
        }

        @DisplayName("when account creation throws an AcmeException")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"keypair.pem", "non-existing.pem"})
        void accountCreationAcmeException(String accountFile) throws AcmeException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Path.of("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session)).thenThrow(new AcmeException());

            assertThrows(SSLCertificateException.class, () -> service.getOrCreateAccountLogin(session));
        }

        @DisplayName("when account doesn't exist, but TOS accepted")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"keypair.pem", "non-existing.pem"})
        void accountNotExistsTOSAccepted(String accountFile) throws AcmeException, MalformedURLException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Path.of("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session))
                    .thenThrow(accountDoesNotExistException)
                    .thenReturn(login);
            when(accountBuilder.addEmail("test@test.com")).thenReturn(accountBuilder);
            when(accountBuilder.agreeToTermsOfService()).thenReturn(accountBuilder);
            when(termsOfServiceService.termsAccepted(TERMS_OF_SERVICE_LINK)).thenReturn(true);
            when(config.getAccountEmail()).thenReturn("test@test.com");

            Login responseLogin = service.getOrCreateAccountLogin(session);
            assertEquals(login, responseLogin);

            verify(accountBuilder, times(1)).addEmail("test@test.com");
            verify(accountBuilder, times(1)).agreeToTermsOfService();

            verify(accountBuilder, atLeastOnce()).createLogin(session);
        }

        @DisplayName("when account does not exist, and TOS not accepted")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"keypair.pem", "non-existing.pem"})
        void accountNotExistsTOSUnaccepted(String accountFile) throws AcmeException, MalformedURLException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Path.of("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session)).thenThrow(accountDoesNotExistException);
            when(termsOfServiceService.termsAccepted(TERMS_OF_SERVICE_LINK)).thenReturn(false);
            when(config.getTermsOfServiceFile()).thenReturn(TERMS_OF_SERVICE_LINK.toString());

            assertThrows(SSLCertificateException.class, () -> service.getOrCreateAccountLogin(session));

            verify(termsOfServiceService, times(1)).writeTermsLink(TERMS_OF_SERVICE_LINK, false);
        }

        @DisplayName("when AcmeUserActionRequiredException occurs when new account creation is attempted")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"keypair.pem", "non-existing.pem"})
        void acmeUserActionRequiredException(String accountFile) throws AcmeException, MalformedURLException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Path.of("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session))
                    .thenThrow(accountDoesNotExistException)
                    .thenThrow(mock(AcmeUserActionRequiredException.class));
            when(accountBuilder.addEmail("test@test.com")).thenReturn(accountBuilder);
            when(accountBuilder.agreeToTermsOfService()).thenReturn(accountBuilder);
            when(termsOfServiceService.termsAccepted(TERMS_OF_SERVICE_LINK)).thenReturn(true);
            when(config.getTermsOfServiceFile()).thenReturn(TERMS_OF_SERVICE_LINK.toString());
            when(config.getAccountEmail()).thenReturn("test@test.com");

            assertThrows(SSLCertificateException.class, () -> service.getOrCreateAccountLogin(session));

            verify(termsOfServiceService, times(1)).writeTermsLink(TERMS_OF_SERVICE_LINK, false);
        }

        @DisplayName("when AcmeException occurs when new account creation is attempted")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"keypair.pem", "non-existing.pem"})
        void acmeException(String accountFile) throws AcmeException, MalformedURLException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Path.of("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session))
                    .thenThrow(accountDoesNotExistException)
                    .thenThrow(new AcmeException());
            when(accountBuilder.addEmail("test@test.com")).thenReturn(accountBuilder);
            when(accountBuilder.agreeToTermsOfService()).thenReturn(accountBuilder);
            when(termsOfServiceService.termsAccepted(TERMS_OF_SERVICE_LINK)).thenReturn(true);
            when(config.getAccountEmail()).thenReturn("test@test.com");

            assertThrows(SSLCertificateException.class, () -> service.getOrCreateAccountLogin(session));
        }

        @AfterEach
        void tearDown() throws IOException {
            Files.deleteIfExists(Path.of("src", "test", "resources", "non-existing.pem"));
        }
    }
}