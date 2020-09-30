package net.eightlives.friendlyssl.exception;

public class SSLCertificateException extends RuntimeException {

    public SSLCertificateException(Throwable throwable) {
        super("Exception while handling SSL certificate management", throwable);
    }

    public SSLCertificateException(String message) {
        super("Exception while handling SSL certificate management: " + message);
    }
}
