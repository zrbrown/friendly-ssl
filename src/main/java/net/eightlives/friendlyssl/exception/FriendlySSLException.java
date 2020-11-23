package net.eightlives.friendlyssl.exception;

public class FriendlySSLException extends RuntimeException {

    public FriendlySSLException(Throwable throwable) {
        super("Exception while handling SSL certificate management", throwable);
    }

    public FriendlySSLException(String message) {
        super("Exception while handling SSL certificate management: " + message);
    }
}
