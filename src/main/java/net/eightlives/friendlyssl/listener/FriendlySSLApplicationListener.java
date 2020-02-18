package net.eightlives.friendlyssl.listener;

import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import net.eightlives.friendlyssl.task.RecursiveTimerTask;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Timer;

@Component
public class FriendlySSLApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    private final SSLCertificateCreateRenewService createRenewService;

    public FriendlySSLApplicationListener(SSLCertificateCreateRenewService createRenewService) {
        this.createRenewService = createRenewService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        Timer timer = new Timer("SSL Certificate Monitor", true);
        timer.schedule(
                new RecursiveTimerTask(timer, createRenewService::createOrRenew),
                Date.from(Instant.now().plus(1, ChronoUnit.SECONDS)));
    }
}
