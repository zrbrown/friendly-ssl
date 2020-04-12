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
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeUserActionRequiredException;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcmeAccountServiceTest {

    private AcmeAccountService service;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private TermsOfServiceService termsOfServiceService;
    @Mock
    private AccountBuilderFactory accountBuilderFactory;
    @Mock
    private Session session;

    @BeforeEach
    void setUp() {
        service = new AcmeAccountService(config, termsOfServiceService, accountBuilderFactory);
    }

    @DisplayName("getting TOS link throws SSLCertificateException")
    @Test
    void getTermsOfServiceLinkException() {
        when(termsOfServiceService.getTermsOfServiceLink(session)).thenThrow(
                new SSLCertificateException(new RuntimeException()));

        assertThrows(SSLCertificateException.class, () -> service.getOrCreateAccountLogin(session));
    }

    @DisplayName("Valid TOS link")
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
            accountKeyPair = KeyPairUtils.readKeyPair(new FileReader(
                    Paths.get("src", "test", "resources", "account.pem").toString()));
            when(termsOfServiceService.getTermsOfServiceLink(session)).thenReturn(TERMS_OF_SERVICE_LINK);

            when(accountBuilderFactory.accountBuilder()).thenReturn(accountBuilder);
            when(accountBuilder.useKeyPair(any(KeyPair.class))).thenReturn(accountBuilder);
            when(accountBuilder.onlyExisting()).thenReturn(accountBuilder);
        }

        @DisplayName("when account key value file exists, it is used")
        @Nested
        class AccountKeyValueFileExists {

            private final String accountFile = Paths.get("src", "test", "resources", "account.pem").toString();

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

            @DisplayName("when account creation fails, but TOS accepted")
            @Test
            void accountCreationFailedTOSAccepted() throws AcmeException {
                when(accountBuilder.createLogin(session))
                        .thenThrow(new AcmeException())
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
        @ValueSource(strings = {"account.pem", "non-existing.pem"})
        void accountCreationSuccessful(String accountFile) throws AcmeException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Paths.get("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session)).thenReturn(login);

            Login responseLogin = service.getOrCreateAccountLogin(session);
            assertEquals(login, responseLogin);

            verify(accountBuilder, times(1)).onlyExisting();
            verify(accountBuilder, times(1)).createLogin(session);
        }

        @DisplayName("when account creation fails, but TOS accepted")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"account.pem", "non-existing.pem"})
        void accountCreationFailedTOSAccepted(String accountFile) throws AcmeException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Paths.get("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session))
                    .thenThrow(new AcmeException())
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

        @DisplayName("when account creation fails, but TOS not accepted")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"account.pem", "non-existing.pem"})
        void accountCreationFailedTOSUnaccepted(String accountFile) throws AcmeException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Paths.get("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session)).thenThrow(new AcmeException());
            when(termsOfServiceService.termsAccepted(TERMS_OF_SERVICE_LINK)).thenReturn(false);
            when(config.getTermsOfServiceFile()).thenReturn(TERMS_OF_SERVICE_LINK.toString());

            assertThrows(SSLCertificateException.class, () -> service.getOrCreateAccountLogin(session));

            verify(termsOfServiceService, times(1)).writeTermsLink(TERMS_OF_SERVICE_LINK, false);
        }

        @DisplayName("when AcmeUserActionRequiredException occurs")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"account.pem", "non-existing.pem"})
        void acmeUserActionRequiredException(String accountFile) throws AcmeException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Paths.get("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session))
                    .thenThrow(new AcmeException())
                    .thenThrow(mock(AcmeUserActionRequiredException.class));
            when(accountBuilder.addEmail("test@test.com")).thenReturn(accountBuilder);
            when(accountBuilder.agreeToTermsOfService()).thenReturn(accountBuilder);
            when(termsOfServiceService.termsAccepted(TERMS_OF_SERVICE_LINK)).thenReturn(true);
            when(config.getTermsOfServiceFile()).thenReturn(TERMS_OF_SERVICE_LINK.toString());
            when(config.getAccountEmail()).thenReturn("test@test.com");

            assertThrows(SSLCertificateException.class, () -> service.getOrCreateAccountLogin(session));

            verify(termsOfServiceService, times(1)).writeTermsLink(TERMS_OF_SERVICE_LINK, false);
        }

        @DisplayName("when AcmeException occurs when account creation fails and TOS accepted")
        @ParameterizedTest(name = "for file {0}")
        @ValueSource(strings = {"account.pem", "non-existing.pem"})
        void acmeException(String accountFile) throws AcmeException {
            when(config.getAccountPrivateKeyFile())
                    .thenReturn(Paths.get("src", "test", "resources", accountFile).toString());
            when(accountBuilder.createLogin(session))
                    .thenThrow(new AcmeException())
                    .thenThrow(new AcmeException());
            when(accountBuilder.addEmail("test@test.com")).thenReturn(accountBuilder);
            when(accountBuilder.agreeToTermsOfService()).thenReturn(accountBuilder);
            when(termsOfServiceService.termsAccepted(TERMS_OF_SERVICE_LINK)).thenReturn(true);
            when(config.getAccountEmail()).thenReturn("test@test.com");

            assertThrows(SSLCertificateException.class, () -> service.getOrCreateAccountLogin(session));
        }

        @AfterEach
        void tearDown() throws IOException {
            Files.deleteIfExists(Paths.get("src", "test", "resources", "non-existing.pem"));
        }
    }
}