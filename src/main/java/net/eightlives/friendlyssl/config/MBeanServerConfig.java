package net.eightlives.friendlyssl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;

@Configuration
public class MBeanServerConfig {

    @Bean
    public MBeanServer mBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }
}
