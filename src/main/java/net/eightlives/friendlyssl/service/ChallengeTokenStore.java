package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ChallengeTokenStore {

    private final Map<String, String> tokensToContent = new HashMap<>();

    /**
     * Return map of tokens mapped to their associated content.
     *
     * @return the tokens to content map.
     */
    public Map<String, String> getTokens() {
        return tokensToContent;
    }

    /**
     * Associate a token with content.
     *
     * @param token   token with which to associate {@code content}
     * @param content content to associate to {@code token}
     */
    public void setToken(String token, String content) {
        log.debug("Token " + token + " with content " + content + " added to token store");
        tokensToContent.put(token, content);
    }
}
