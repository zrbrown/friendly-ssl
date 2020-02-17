package net.eightlives.friendlyssl.listener;

import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import net.eightlives.friendlyssl.task.ReactiveTimerTask;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

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
                new ReactiveTimerTask(timer, createRenewService::createOrRenew),
                Instant.now().plus(30, ChronoUnit.SECONDS));
    }
}
