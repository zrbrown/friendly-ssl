package net.eightlives.friendlyssl.listener;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import net.eightlives.friendlyssl.task.RecursiveTimerTask;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Timer;

@Slf4j
@Component
public class FriendlySSLApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    private final FriendlySSLConfig config;
    private final SSLCertificateCreateRenewService createRenewService;

    public FriendlySSLApplicationListener(FriendlySSLConfig config,
                                          SSLCertificateCreateRenewService createRenewService) {
        this.config = config;
        this.createRenewService = createRenewService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        if (config.isAutoRenewEnabled()) {
            log.info("Auto-renew SSL enabled, starting timer");
            Timer timer = new Timer("SSL Certificate Monitor", true);
            timer.schedule(
                    new RecursiveTimerTask(timer, createRenewService::createOrRenew),
                    Date.from(Instant.now().plus(1, ChronoUnit.SECONDS)));
        }
    }
}
