package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.exception.KeyStoreGeneratorException;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.engines.RC2Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.pkcs.*;
import org.bouncycastle.pkcs.bc.BcPKCS12MacCalculatorBuilder;
import org.bouncycastle.pkcs.bc.BcPKCS12PBEOutputEncryptorBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS12SafeBagBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class PKCS12KeyStoreService {

    private static final String ROOT_FRIENDLY_NAME = "root";
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String KEYFACTORY_TYPE = "RSA";

    private final FriendlySSLConfig config;
    private final LocalIdGeneratorService localIdGeneratorService;

    public PKCS12KeyStoreService(FriendlySSLConfig config,
                                 LocalIdGeneratorService localIdGeneratorService) {
        this.config = config;
        this.localIdGeneratorService = localIdGeneratorService;
    }

    /**
     * Generate a PKCS12 keystore for the given certificate chain.
     *
     * @param certificates the certificate chain to put in the keystore
     * @param privateKey   the private key used to sign the local certificate. This is the same key that was used for the
     *                     certificate signing request (CSR)
     * @return the byte representation of the generated PKCS12 keystore
     * @throws KeyStoreGeneratorException if an exception occurs while generating the keystore
     */
    public byte[] generateKeyStore(List<X509Certificate> certificates, PrivateKey privateKey) {
        try {
            byte[] localKeyBytes = localIdGeneratorService.generate();

            PKCS12SafeBag[] certBags = new PKCS12SafeBag[certificates.size()];
            for (int i = certificates.size() - 1; i >= 0; i--) {
                var certBagBuilder = new JcaPKCS12SafeBagBuilder(certificates.get(i));
                if (i == 0) {
                    certBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, new DERBMPString(config.getCertificateKeyAlias()));
                    certBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_localKeyId, new DEROctetString(localKeyBytes));
                } else {
                    certBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, new DERBMPString(ROOT_FRIENDLY_NAME));
                }
                certBags[i] = certBagBuilder.build();
            }

            PKCS12SafeBagBuilder keyBagBuilder = new JcaPKCS12SafeBagBuilder(privateKey,
                    new BcPKCS12PBEOutputEncryptorBuilder(
                            PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC,
                            new CBCBlockCipher(new DESedeEngine())).setIterationCount(2048)
                            .build("".toCharArray()));
            keyBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, new DERBMPString(config.getCertificateKeyAlias()));
            keyBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_localKeyId, new DEROctetString(localKeyBytes));

            PKCS12PfxPduBuilder pfxBuilder = new PKCS12PfxPduBuilder();
            pfxBuilder.addEncryptedData(
                    new BcPKCS12PBEOutputEncryptorBuilder(
                            PKCSObjectIdentifiers.pbeWithSHAAnd40BitRC2_CBC,
                            new CBCBlockCipher(new RC2Engine())).setIterationCount(2048)
                            .build("".toCharArray()), certBags);
            pfxBuilder.addData(keyBagBuilder.build());

            BcPKCS12MacCalculatorBuilder macBuilder = new BcPKCS12MacCalculatorBuilder();
            macBuilder.setIterationCount(2048);

            PKCS12PfxPdu pfx = pfxBuilder.build(macBuilder, "".toCharArray());
            return pfx.getEncoded(ASN1Encoding.DL);
        } catch (PKCSException | IOException e) {
            throw new KeyStoreGeneratorException(e);
        }
    }

    /**
     * Return a key pair comprised of a private key from the configured keystore with the given alias and a public
     * key from the given certificate.
     *
     * @param certificate            the certificate that contains the public key
     * @param privateKeyKeyAlias the alias from which to retrieve the key pair
     * @return a key pair comprised of a private key from the configured keystore with the given alias and a public
     * key from the given certificate, or {@code null} if an exception occurs while accessing the keystore
     * while accessing the keystore
     */
    public KeyPair getKeyPair(Certificate certificate, String privateKeyKeyAlias) {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE_TYPE);
            store.load(Files.newInputStream(Path.of(config.getKeystoreFile())), "".toCharArray());

            KeyFactory keyFactory = KeyFactory.getInstance(KEYFACTORY_TYPE);
            Key key = store.getKey(privateKeyKeyAlias, "".toCharArray());
            if (key == null) {
                log.error("Private key alias " + privateKeyKeyAlias +
                        " not found in keystore " + config.getKeystoreFile() +
                        " when loading keystore");
                return null;
            }

            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(key.getEncoded()));

            return new KeyPair(certificate.getPublicKey(), privateKey);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | CertificateException | KeyStoreException
                | UnrecoverableKeyException | IOException e) {
            log.error("Exception while accessing keystore", e);
            return null;
        }
    }

    /**
     * Return a certificate from the configured keystore with the given alias.
     *
     * @param keyAlias the alias of the certificate to retrieve
     * @return the certificate in the keystore with the given alias, or {@link Optional#empty()} if an exception occurs
     * while accessing the keystore
     */
    public Optional<X509Certificate> getCertificate(String keyAlias) {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE_TYPE);

            try {
                store.load(Files.newInputStream(Path.of(config.getKeystoreFile())), "".toCharArray());
            } catch (NoSuchFileException e) {
                return Optional.empty();
            }

            Certificate certificate = store.getCertificate(keyAlias);
            if (certificate instanceof X509Certificate) {
                return Optional.of((X509Certificate) certificate);
            }
            return Optional.empty();
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            log.error("Exception while accessing keystore", e);
            return Optional.empty();
        }
    }
}
