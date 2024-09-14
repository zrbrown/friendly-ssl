package net.eightlives.friendlyssl.model;

import java.time.Instant;

/**
 * A descriptor of a certificate renewal attempt.
 *
 * @param status the status of the renewal attempt
 * @param time   the time at which renewal should be attempted again
 */
public record CertificateRenewal(CertificateRenewalStatus status, Instant time) {
}
