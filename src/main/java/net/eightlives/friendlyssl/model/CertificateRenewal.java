package net.eightlives.friendlyssl.model;

import java.time.Instant;

public class CertificateRenewal {

    private final CertificateRenewalStatus status;
    private final Instant time;

    public CertificateRenewal(CertificateRenewalStatus status, Instant time) {
        this.status = status;
        this.time = time;
    }

    public CertificateRenewalStatus getStatus() {
        return status;
    }

    public Instant getTime() {
        return time;
    }
}
