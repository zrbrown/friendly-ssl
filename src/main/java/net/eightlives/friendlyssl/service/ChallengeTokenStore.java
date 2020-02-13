package net.eightlives.friendlyssl.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ChallengeTokenStore {

    private final Map<String, String> tokensToContent = new HashMap<>();

    public Map<String, String> getTokens() {
        return tokensToContent;
    }

    public void setToken(String token, String content) {
        tokensToContent.put(token, content);
    }
}
