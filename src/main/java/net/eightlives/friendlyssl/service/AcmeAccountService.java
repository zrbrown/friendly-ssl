package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Login;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeUserActionRequiredException;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.KeyPair;

@Slf4j
@Component
public class AcmeAccountService {

    public Login getOrCreateAccountLogin(Session session) {
        try (Reader keyReader = getKeyReader("account.pem")) {
            KeyPair accountKeyPair = KeyPairUtils.readKeyPair(keyReader);
            try {
                return new AccountBuilder()
                        .useKeyPair(accountKeyPair)
                        .onlyExisting()
                        .createLogin(session);
            } catch (AcmeException ignored) {
                return new AccountBuilder()
                        .useKeyPair(accountKeyPair)
                        .addEmail("zackrbrown@gmail.com")
                        .agreeToTermsOfService() // TODO new service to handle user accepting agreement (probably make this a service call, then in mindy have a pretty page to accept)
                        .createLogin(session);
            }
        } catch (AcmeUserActionRequiredException e) {
            log.error("Account retrieval failed due to user action required (terms of service probably changed). See " + e.getInstance());
            throw new SSLCertificateException(e); // TODO same as above TOS agreement
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

                ByteArrayOutputStream keyBytes = new ByteArrayOutputStream();
                KeyPairUtils.writeKeyPair(accountKeyPair, new OutputStreamWriter(keyBytes));
                keyBytes.flush();
                return new InputStreamReader(new ByteArrayInputStream(keyBytes.toByteArray()));
            }
        }
    }
}
