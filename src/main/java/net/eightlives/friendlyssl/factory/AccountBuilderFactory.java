package net.eightlives.friendlyssl.factory;

import org.shredzone.acme4j.AccountBuilder;
import org.springframework.stereotype.Component;

@Component
public class AccountBuilderFactory {

    public AccountBuilder accountBuilder() {
        return new AccountBuilder();
    }
}
