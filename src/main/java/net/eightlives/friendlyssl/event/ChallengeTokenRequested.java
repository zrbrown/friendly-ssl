package net.eightlives.friendlyssl.event;

import org.springframework.context.ApplicationEvent;

public class ChallengeTokenRequested extends ApplicationEvent {

    private final String token;

    public ChallengeTokenRequested(Object source, String token) {
        super(source);
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
