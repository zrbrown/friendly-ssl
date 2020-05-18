package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ChallengeTokenStore {

    private final Map<String, String> tokensToContent = new HashMap<>();

    public Map<String, String> getTokens() {
        return tokensToContent;
    }

    public void setToken(String token, String content) {
        log.debug("Token " + token + " with content " + content + " added to token store");
        tokensToContent.put(token, content);
    }
}
