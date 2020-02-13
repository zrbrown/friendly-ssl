package net.eightlives.friendlyssl.exception;

public class KeyStoreGeneratorException extends RuntimeException {

    public KeyStoreGeneratorException(Throwable throwable) {
        super("Exception occurred while generating keystore", throwable);
    }
}
