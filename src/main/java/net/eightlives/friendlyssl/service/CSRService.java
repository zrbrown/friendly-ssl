package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.springframework.stereotype.Component;

import java.security.KeyPair;

@Component
public class CSRService {

    public byte[] generateCSR(String domain, KeyPair domainKeyPair) {
        CSRBuilder csrBuilder = new CSRBuilder();
        csrBuilder.addDomain(domain);

        try {
            csrBuilder.sign(domainKeyPair);
            return csrBuilder.getEncoded();
        } catch (Exception e) {
            throw new SSLCertificateException(e);
        }
    }
}
