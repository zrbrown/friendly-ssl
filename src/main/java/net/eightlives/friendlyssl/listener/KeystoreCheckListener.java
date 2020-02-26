package net.eightlives.friendlyssl.listener;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.engines.RC2Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.pkcs.*;
import org.bouncycastle.pkcs.bc.BcPKCS12MacCalculatorBuilder;
import org.bouncycastle.pkcs.bc.BcPKCS12PBEOutputEncryptorBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS12SafeBagBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
public class KeystoreCheckListener implements SpringApplicationRunListener {

    private static final String KEYSTORE_TYPE = "PKCS12";

    private final SpringApplication application;

    public KeystoreCheckListener(SpringApplication application, String[] args) {
        this.application = application;
    }

    @Override
    public void starting() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        application.getAllSources().stream()
                .filter(source -> source == application.getMainApplicationClass())
                .findFirst()
                .ifPresent(unused -> {
                    createSelfSignedIfKeystoreInvalid(
                            environment.getProperty("friendly-ssl.keystore-file"),
                            environment.getProperty("friendly-ssl.certificate-friendly-name"),
                            environment.getProperty("friendly-ssl.domain"));
                });
    }


    private void createSelfSignedIfKeystoreInvalid(String keystoreLocation, String certificateFriendlyName,
                                                   String domain) {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE_TYPE);

            File keystoreFile = new File(keystoreLocation);

            Certificate certificate = null;
            try {
                store.load(new FileInputStream(keystoreFile), "".toCharArray());
                certificate = store.getCertificate(certificateFriendlyName);
            } catch (FileNotFoundException ignored) {
                keystoreFile.createNewFile();
            } catch (KeyStoreException ignored) {
            }

            if (certificate == null) {
                try (OutputStream file = new FileOutputStream(keystoreFile)) {
                    file.write(generateSelfSignedCertificateKeystore(certificateFriendlyName, domain));
                }
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace(); //TODO
        }
    }

    private byte[] generateSelfSignedCertificateKeystore(String certificateFriendlyName, String domain) {
        try {
            KeyPair keyPair = KeyPairUtils.createKeyPair(2048);
            X500Name name = new X500Name("CN=" + domain + ",DC=FRIENDLYSSL,DC=EIGHTLIVES,DC=NET");
            AlgorithmIdentifier signatureAlgorithmId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WITHRSA");
            AlgorithmIdentifier digestAlgorithmId = new DefaultDigestAlgorithmIdentifierFinder().find(signatureAlgorithmId);
            ContentSigner signer = new BcRSAContentSignerBuilder(signatureAlgorithmId, digestAlgorithmId).build(
                    PrivateKeyFactory.createKey(keyPair.getPrivate().getEncoded()));

            org.bouncycastle.asn1.x509.Certificate certificate = new X509v3CertificateBuilder(
                    name, new BigInteger(64, new SecureRandom()),
                    Date.from(Instant.now()), Date.from(Instant.now().plus(1, ChronoUnit.DAYS)),
                    name, SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded())
            ).build(signer).toASN1Structure();
            var certBagBuilder = new PKCS12SafeBagBuilder(certificate);
            certBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, new DERBMPString(certificateFriendlyName));
            PKCS12SafeBag[] certBags = new PKCS12SafeBag[]{certBagBuilder.build()};

            PKCS12SafeBagBuilder keyBagBuilder = new JcaPKCS12SafeBagBuilder(keyPair.getPrivate(),
                    new BcPKCS12PBEOutputEncryptorBuilder(
                            PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC,
                            new CBCBlockCipher(new DESedeEngine())).setIterationCount(2048)
                            .build("".toCharArray()));
            keyBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, new DERBMPString(certificateFriendlyName));

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
        } catch (IOException | PKCSException | OperatorCreationException e) {
            e.printStackTrace(); //TODO
            throw new RuntimeException();
        }
    }

    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
    }

    @Override
    public void contextLoaded(ConfigurableApplicationContext context) {
    }

    @Override
    public void started(ConfigurableApplicationContext context) {
    }

    @Override
    public void running(ConfigurableApplicationContext context) {
    }

    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
    }
}
