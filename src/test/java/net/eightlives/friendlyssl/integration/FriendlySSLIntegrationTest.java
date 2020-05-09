package net.eightlives.friendlyssl.integration;

import net.eightlives.friendlyssl.annotation.FriendlySSL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@Tag("slow")
@SpringBootTest
@ActiveProfiles("friendly-ssl,test")
class FriendlySSLIntegrationTest {

    @FriendlySSL
    @SpringBootApplication
    static class TestApp {
    }

    @Test
    void getToken() {

    }
}