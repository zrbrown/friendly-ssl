package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.junit.BiUUIDStringProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Execution(ExecutionMode.CONCURRENT)
class ChallengeTokenStoreTest {

    private ChallengeTokenStore store;

    @BeforeEach
    void setUp() {
        store = new ChallengeTokenStore();
    }

    @DisplayName("Testing token store")
    @ParameterizedTest(name = "for token {0}, content {1}")
    @ArgumentsSource(BiUUIDStringProvider.class)
    void store(String token, String content) {
        assertFalse(store.getTokens().containsKey(token));

        store.setToken(token, content);

        assertEquals(store.getTokens().get(token), content);
    }
}