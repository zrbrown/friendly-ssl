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

    public byte[] generateKeyStore(List<X509Certificate> certificates, PrivateKey privateKey) {
        try {
            byte[] localKeyBytes = localIdGeneratorService.generate();

            PKCS12SafeBag[] certBags = new PKCS12SafeBag[certificates.size()];
            for (int i = certificates.size() - 1; i >= 0; i--) {
                var certBagBuilder = new JcaPKCS12SafeBagBuilder(certificates.get(i));
                if (i == 0) {
                    certBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, new DERBMPString(config.getCertificateFriendlyName()));
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
            keyBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, new DERBMPString(config.getCertificateFriendlyName()));
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

    public KeyPair getKeyPair(Certificate certificate, String privateKeyFriendlyName) {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE_TYPE);
            store.load(Files.newInputStream(Path.of(config.getKeystoreFile())), "".toCharArray());

            KeyFactory keyFactory = KeyFactory.getInstance(KEYFACTORY_TYPE);
            Key key = store.getKey(privateKeyFriendlyName, "".toCharArray());
            if (key == null) {
                log.error("Private key friendly name " + privateKeyFriendlyName +
                        " not found in keystore " + config.getKeystoreFile() +
                        " when loading keystore");
                return null;
            }

            PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(key.getEncoded()));

            return new KeyPair(certificate.getPublicKey(), privateKey);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | CertificateException | KeyStoreException
                | UnrecoverableKeyException | IOException e) {
            log.error("Exception while loading keystore", e);
            return null;
        }
    }

    public Optional<X509Certificate> getCertificate(String friendlyName) {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE_TYPE);

            try {
                store.load(Files.newInputStream(Path.of(config.getKeystoreFile())), "".toCharArray());
            } catch (NoSuchFileException e) {
                return Optional.empty();
            }

            Certificate certificate = store.getCertificate(friendlyName);
            if (certificate instanceof X509Certificate) {
                return Optional.of((X509Certificate) certificate);
            }
            return Optional.empty();
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            log.error("Exception while loading keystore", e);
            return Optional.empty();
        }
    }
}
