package net.eightlives.friendlyssl.service;

import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.stream.Collectors;

@Component
public class LocalIdGeneratorService {

    /**
     * Generate random octets for the local key when generating a PKCS12 keystore.
     *
     * @return randomly generated octets
     */
    public byte[] generate() {
        String localKeyId = new Random().ints(5)
                .mapToObj(Integer::toHexString)
                .collect(Collectors.joining());

        byte[] localKeyBytes = new byte[localKeyId.length() / 2];
        for (int i = 0; i < localKeyBytes.length; i++) {
            int index = i * 2;
            int hex = Integer.parseInt(localKeyId.substring(index, index + 2), 16);
            localKeyBytes[i] = (byte) hex;
        }

        return localKeyBytes;
    }
}
