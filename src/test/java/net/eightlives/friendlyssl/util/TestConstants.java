package net.eightlives.friendlyssl.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TestConstants {
    public static final Instant EXISTING_KEYSTORE_CERT_EXPIRATION = Instant.from(OffsetDateTime.of(2012, 12, 22, 7, 41, 51, 0, ZoneOffset.UTC));
}
