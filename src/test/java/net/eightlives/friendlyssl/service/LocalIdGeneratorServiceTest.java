package net.eightlives.friendlyssl.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalIdGeneratorServiceTest {

    private LocalIdGeneratorService service;

    @BeforeEach
    void setUp() {
        service = new LocalIdGeneratorService();
    }

    @DisplayName("Test that a valid local ID is generated")
    @RepeatedTest(value = 5)
    void validLocalId() {
        byte[] localId = service.generate();

        StringBuilder builder = new StringBuilder();
        for (byte b : localId) {
            builder.append(Integer.toHexString((int) b & 0xFF));
        }

        for (char c : builder.toString().toCharArray()) {
            assertTrue(Character.digit(c, 16) > -1);
        }
    }
}