package net.eightlives.friendlyssl.listener;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.factory.RecursiveTimerTaskFactory;
import net.eightlives.friendlyssl.service.AutoRenewService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.security.Security;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class FriendlySSLApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    private final FriendlySSLConfig config;
    private final AutoRenewService autoRenewService;
    private final RecursiveTimerTaskFactory timerTaskFactory;
    private final ScheduledExecutorService timer;

    public FriendlySSLApplicationListener(FriendlySSLConfig config,
                                          AutoRenewService autoRenewService,
                                          RecursiveTimerTaskFactory timerTaskFactory,
                                          @Qualifier("ssl-certificate-monitor") ScheduledExecutorService timer) {
        this.config = config;
        this.autoRenewService = autoRenewService;
        this.timerTaskFactory = timerTaskFactory;
        this.timer = timer;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        Security.addProvider(new BouncyCastleProvider());

        if (config.isAutoRenewEnabled()) {
            log.info("Auto-renew SSL enabled, starting timer");
            timer.schedule(timerTaskFactory.create(timer, this::autoRenewTime), 1, TimeUnit.SECONDS);
        }
    }

    private Instant autoRenewTime() {
        return autoRenewService.autoRenew().getTime();
    }
}
