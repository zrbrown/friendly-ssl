package net.eightlives.friendlyssl.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class TestConstants {
    public static final Instant EXISTING_KEYSTORE_CERT_EXPIRATION = Instant.from(OffsetDateTime.of(2020, 9, 23, 3, 4, 15, 0, ZoneOffset.UTC));
    public static final String PEBBLE_TOS_LINK = "data:text/plain,Do%20what%20thou%20wilt";
}
