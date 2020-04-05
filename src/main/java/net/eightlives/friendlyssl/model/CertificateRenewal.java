package net.eightlives.friendlyssl.model;

import java.time.Instant;

public record CertificateRenewal(CertificateRenewalStatus status, Instant time) {}
