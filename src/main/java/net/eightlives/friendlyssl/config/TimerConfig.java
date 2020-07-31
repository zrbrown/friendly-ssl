package net.eightlives.friendlyssl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class TimerConfig {

    @Bean(name = "ssl-certificate-monitor")
    public ScheduledExecutorService timer() {
        return Executors.newSingleThreadScheduledExecutor();
    }
}
