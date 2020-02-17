package net.eightlives.friendlyssl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "friendly-ssl")
public class FriendlySSLConfig {
}
