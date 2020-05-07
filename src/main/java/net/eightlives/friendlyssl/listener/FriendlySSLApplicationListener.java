package net.eightlives.friendlyssl.listener;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.factory.RecursiveTimerTaskFactory;
import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import net.eightlives.friendlyssl.task.RecursiveTimerTask;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.security.Security;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Timer;

@Slf4j
@Component
public class FriendlySSLApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    private final FriendlySSLConfig config;
    private final SSLCertificateCreateRenewService createRenewService;
    private final RecursiveTimerTaskFactory timerTaskFactory;
    private final Clock clock;
    private final Timer timer;

    public FriendlySSLApplicationListener(FriendlySSLConfig config,
                                          SSLCertificateCreateRenewService createRenewService,
                                          RecursiveTimerTaskFactory timerTaskFactory,
                                          Clock clock,
                                          @Qualifier("ssl-certificate-monitor") Timer timer) {
        this.config = config;
        this.createRenewService = createRenewService;
        this.timerTaskFactory = timerTaskFactory;
        this.clock = clock;
        this.timer = timer;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        Security.addProvider(new BouncyCastleProvider());

        if (config.isAutoRenewEnabled()) {
            log.info("Auto-renew SSL enabled, starting timer");
            timer.schedule(
                    timerTaskFactory.create(timer, this::createOrRenewTime),
                    Date.from(clock.instant().plus(1, ChronoUnit.SECONDS)));
        }
    }

    private Instant createOrRenewTime() {
        return createRenewService.createOrRenew().getTime();
    }
}
