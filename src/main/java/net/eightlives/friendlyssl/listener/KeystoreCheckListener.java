package net.eightlives.friendlyssl.listener;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Application listener that adds security provider(s) and checks for an existing keystore. Because Spring will not
 * start if it has SSL enabled and there is no keystore or a keystore without the given key alias, in these cases
 * a self-signed certificate is generated and will overwrite any existing keystore with the configured name.
 * A keystore with a password or a corrupted/inaccessible will be logged and ignored, likely causing Spring to not start.
 * If the configured keystore and key alias are found, no action is performed and Spring should start.
 */
@Slf4j
public class KeystoreCheckListener implements SpringApplicationRunListener {

    private static final String KEYSTORE_TYPE = "PKCS12";

    public KeystoreCheckListener(SpringApplication application, String[] args) {
    }

    @Override
    public void starting() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public void environmentPrepared(ConfigurableEnvironment environment) {
        String keystoreLocation = environment.getProperty("friendly-ssl.keystore-file");
        String certificateFriendlyName = environment.getProperty("friendly-ssl.certificate-key-alias");
        String domain = environment.getProperty("friendly-ssl.domain");

        if (keystoreLocation != null && certificateFriendlyName != null && domain != null) {
            createSelfSignedIfKeystoreInvalid(keystoreLocation, certificateFriendlyName, domain);
        }
    }

    private void createSelfSignedIfKeystoreInvalid(String keystoreLocation, String certificateFriendlyName,
                                                   String domain) {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE_TYPE);
            Path keystorePath = Path.of(keystoreLocation);
            Certificate certificate = null;

            try {
                if (keystorePath.getParent() != null) {
                    Files.createDirectories(keystorePath.getParent());
                }
                Files.createFile(keystorePath);
                log.info("Keystore file " + keystoreLocation + " created.");
            } catch (FileAlreadyExistsException e) {
                store.load(new FileInputStream(keystorePath.toFile()), "".toCharArray());
                log.info("Existing keystore file " + keystoreLocation + " loaded.");
                certificate = store.getCertificate(certificateFriendlyName);
                log.info("Existing keystore file " + keystoreLocation + " contains certificate named " + certificateFriendlyName + ": " + (certificate != null));
            }

            if (certificate == null) {
                try (OutputStream file = new FileOutputStream(keystorePath.toFile())) {
                    file.write(generateSelfSignedCertificateKeystore(certificateFriendlyName, domain));
                    log.info("Self-signed certificate named " + certificateFriendlyName);
                }
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            if (e.getCause() instanceof UnrecoverableKeyException) {
                log.error("Cannot load keystore file " + keystoreLocation + " - likely due to keystore having a password, which is unsupported.");
            } else {
                log.error("Error while validating certificate on startup", e);
            }
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
            PKCS12SafeBagBuilder certBagBuilder = new PKCS12SafeBagBuilder(certificate);
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
            log.error("Error while generating self-signed certificate", e);
            throw new SSLCertificateException(e);
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
