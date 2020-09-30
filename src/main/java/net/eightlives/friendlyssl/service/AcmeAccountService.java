package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import net.eightlives.friendlyssl.factory.AccountBuilderFactory;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeServerException;
import org.shredzone.acme4j.exception.AcmeUserActionRequiredException;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPair;

@Slf4j
@Component
public class AcmeAccountService {

    private static final URI ACCOUNT_NOT_EXISTS = URI.create("urn:ietf:params:acme:error:accountDoesNotExist");

    private final FriendlySSLConfig config;
    private final TermsOfServiceService termsOfServiceService;
    private final AccountBuilderFactory accountBuilderFactory;

    public AcmeAccountService(FriendlySSLConfig config,
                              TermsOfServiceService termsOfServiceService,
                              AccountBuilderFactory accountBuilderFactory) {
        this.config = config;
        this.termsOfServiceService = termsOfServiceService;
        this.accountBuilderFactory = accountBuilderFactory;
    }

    public Login getOrCreateAccountLogin(Session session) {
        URI termsOfServiceLink = termsOfServiceService.getTermsOfServiceLink(session);

        try (Reader keyReader = getKeyReader(config.getAccountPrivateKeyFile())) {
            KeyPair accountKeyPair = KeyPairUtils.readKeyPair(keyReader);
            try {
                Login login = accountBuilderFactory.accountBuilder()
                        .useKeyPair(accountKeyPair)
                        .onlyExisting()
                        .createLogin(session);
                log.info("Using existing account login");
                return login;
            } catch (AcmeServerException e) {
                URI exceptionType = e.getProblem().getType();
                if (exceptionType.equals(ACCOUNT_NOT_EXISTS)) {
                    if (!termsOfServiceService.termsAccepted(termsOfServiceLink)) {
                        termsOfServiceService.writeTermsLink(termsOfServiceLink, false);
                        throw new SSLCertificateException(
                                "Account does not exist. Terms of service must be accepted in file " + config.getTermsOfServiceFile() + " before account can be created");
                    }

                    log.info("Account does not exist. Creating account.");
                    return accountBuilderFactory.accountBuilder()
                            .useKeyPair(accountKeyPair)
                            .addEmail(config.getAccountEmail())
                            .agreeToTermsOfService()
                            .createLogin(session);
                }

                throw e;
            }
        } catch (AcmeUserActionRequiredException e) {
            log.error("Account retrieval failed due to user action required (terms of service probably changed). See " + e.getInstance() +
                    " and if the terms of service did change, accept the terms in file " + config.getTermsOfServiceFile(), e);
            termsOfServiceService.writeTermsLink(termsOfServiceLink, false);
            throw new SSLCertificateException(e);
        } catch (IOException | AcmeException e) {
            log.error("Error while retrieving or creating ACME Login");
            throw new SSLCertificateException(e);
        }
    }

    private Reader getKeyReader(String filename) throws IOException {
        try {
            return Files.newBufferedReader(Path.of(filename));
        } catch (NoSuchFileException e) {
            KeyPair accountKeyPair = KeyPairUtils.createKeyPair(2048);
            try (Writer fileWriter = Files.newBufferedWriter(Path.of(filename))) {
                KeyPairUtils.writeKeyPair(accountKeyPair, fileWriter);

                ByteArrayOutputStream keyBytes = new ByteArrayOutputStream();
                KeyPairUtils.writeKeyPair(accountKeyPair, new OutputStreamWriter(keyBytes));
                keyBytes.flush();
                return new InputStreamReader(new ByteArrayInputStream(keyBytes.toByteArray()));
            }
        }
    }
}
