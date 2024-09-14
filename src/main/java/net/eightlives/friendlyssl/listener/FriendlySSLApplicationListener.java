package net.eightlives.friendlyssl.listener;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.factory.RecursiveTimerTaskFactory;
import net.eightlives.friendlyssl.service.AutoRenewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Application startup listener that starts the auto-renew service if it is enabled.
 */
@Component
public class FriendlySSLApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(FriendlySSLApplicationListener.class);

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
        if (config.isAutoRenewEnabled()) {
            LOG.info("Auto-renew SSL enabled, starting timer");
            timer.schedule(timerTaskFactory.create(timer, this::autoRenewTime), 1, TimeUnit.SECONDS);
        }
    }

    private Instant autoRenewTime() {
        return autoRenewService.autoRenew().time();
    }
}
