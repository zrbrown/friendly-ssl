package net.eightlives.friendlyssl.model;

import java.time.Instant;

/**
 * A descriptor of a certificate renewal attempt.
 */
public class CertificateRenewal {

    private final CertificateRenewalStatus status;
    private final Instant time;

    public CertificateRenewal(CertificateRenewalStatus status, Instant time) {
        this.status = status;
        this.time = time;
    }

    /**
     * @return the status of the renewal attempt
     */
    public CertificateRenewalStatus getStatus() {
        return status;
    }

    /**
     * @return the time at which renewal should be attempted again
     */
    public Instant getTime() {
        return time;
    }
}
