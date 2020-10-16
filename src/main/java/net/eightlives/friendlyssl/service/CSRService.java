package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.springframework.stereotype.Component;

import java.security.KeyPair;

@Component
public class CSRService {

    /**
     * Generate a certificate signing request (CSR).
     *
     * @param domain        the domain being certified
     * @param domainKeyPair the key pair with which to sign the CSR
     * @return the encoded certification request
     * @throws SSLCertificateException if an exception occurs while signing the CSR
     */
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
