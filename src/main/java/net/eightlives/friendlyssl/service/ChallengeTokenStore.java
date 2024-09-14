package net.eightlives.friendlyssl.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ChallengeTokenStore {

    private static final Logger LOG = LoggerFactory.getLogger(ChallengeTokenStore.class);

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
        LOG.debug("Token {} with content {} added to token store", token, content);
        tokensToContent.put(token, content);
    }
}
