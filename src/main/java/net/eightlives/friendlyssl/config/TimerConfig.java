package net.eightlives.friendlyssl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Timer;

@Configuration
public class TimerConfig {

    @Bean(name = "ssl-certificate-monitor")
    public Timer timer() {
        return new Timer("SSL Certificate Monitor", true);
    }
}
