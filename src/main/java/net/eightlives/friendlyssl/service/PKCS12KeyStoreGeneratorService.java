package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.exception.KeyStoreGeneratorException;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.crypto.engines.DESedeEngine;
import org.bouncycastle.crypto.engines.RC2Engine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pkcs.*;
import org.bouncycastle.pkcs.bc.BcPKCS12MacCalculatorBuilder;
import org.bouncycastle.pkcs.bc.BcPKCS12PBEOutputEncryptorBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS12SafeBagBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Component
public class PKCS12KeyStoreGeneratorService {

    private static final String ROOT_FRIENDLY_NAME = "root";

    public byte[] generateKeyStore(List<X509Certificate> certificates, PrivateKey privateKey) {
        try {
            Security.addProvider(new BouncyCastleProvider());

            String localKeyId = new Random().ints(5)
                    .mapToObj(Integer::toHexString)
                    .collect(Collectors.joining());
            byte[] localKeyBytes = new byte[localKeyId.length() / 2];
            for (int i = 0; i < localKeyBytes.length; i++) {
                int index = i * 2;
                int hex = Integer.parseInt(localKeyId.substring(index, index + 2), 16);
                localKeyBytes[i] = (byte) hex;
            }

            PKCS12SafeBag[] certBags = new PKCS12SafeBag[certificates.size()];
            for (int i = certificates.size() - 1; i >= 0; i--) {
                var certBagBuilder = new JcaPKCS12SafeBagBuilder(certificates.get(i));
                if (i == 0) {
                    certBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, new DERBMPString("tomcat"));
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
            keyBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName, new DERBMPString("tomcat"));
            keyBagBuilder.addBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_localKeyId, new DEROctetString(localKeyBytes));

            PKCS12PfxPduBuilder pfxBuilder = new PKCS12PfxPduBuilder();
            pfxBuilder.addEncryptedData(
                    new BcPKCS12PBEOutputEncryptorBuilder(
                            PKCSObjectIdentifiers.pbeWithSHAAnd40BitRC2_CBC,
                            new CBCBlockCipher(new RC2Engine())).setIterationCount(2048)
                            .build("".toCharArray()), certBags);
            pfxBuilder.addData(keyBagBuilder.build());

            var macBuilder = new BcPKCS12MacCalculatorBuilder();
            macBuilder.setIterationCount(2048);

            PKCS12PfxPdu pfx = pfxBuilder.build(macBuilder, "".toCharArray());
            return pfx.getEncoded(ASN1Encoding.DL);
        } catch (PKCSException | IOException e) {
            throw new KeyStoreGeneratorException(e);
        }
    }
}
