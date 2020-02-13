package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.KeyPair;

@Slf4j
@Component
public class AcmeAccountService {

    public Login getOrCreateAccountLogin(Session session) {
        try (Reader keyReader = getKeyReader("account.pem")) {
            AccountBuilder accountBuilder = new AccountBuilder()
                    .useKeyPair(KeyPairUtils.readKeyPair(keyReader));

            try {
                return accountBuilder
                        .onlyExisting()
                        .createLogin(session);
            } catch (AcmeException ignored) {
                return accountBuilder
                        .addEmail("acme@example.com") //TODO email config
                        .agreeToTermsOfService()
                        .createLogin(session);
            }
        } catch (IOException | AcmeException e) {
            log.error("Error while retrieving or creating ACME Login");
            throw new SSLCertificateException(e);
        }
    }

    private Reader getKeyReader(String filename) throws IOException {
        try {
            return new FileReader(filename);
        } catch (FileNotFoundException fnfe) {
            KeyPair accountKeyPair = KeyPairUtils.createKeyPair(2048);
            try (FileWriter fileWriter = new FileWriter("account.pem")) {
                KeyPairUtils.writeKeyPair(accountKeyPair, fileWriter);

                PipedWriter directKeyWriter = new PipedWriter();
                KeyPairUtils.writeKeyPair(accountKeyPair, directKeyWriter);
                return new PipedReader(directKeyWriter);
            }
        }
    }
}
