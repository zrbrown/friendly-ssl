package net.eightlives.friendlyssl.integration;

import net.eightlives.friendlyssl.annotation.PebbleTest;
import net.eightlives.friendlyssl.annotation.FriendlySSL;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.context.ActiveProfiles;

@PebbleTest
@ActiveProfiles("test")
class FriendlySSLIntegrationTest {

    @FriendlySSL
    @SpringBootApplication
    static class TestApp {
    }

    @Test
    void getToken() {


    }
}
