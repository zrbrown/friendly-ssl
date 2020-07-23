package net.eightlives.friendlyssl.annotation;

import net.eightlives.friendlyssl.config.ClockConfig;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.config.SchedulerConfig;
import net.eightlives.friendlyssl.config.TimerConfig;
import net.eightlives.friendlyssl.controller.CertificateChallengeController;
import net.eightlives.friendlyssl.controller.CertificateController;
import net.eightlives.friendlyssl.controller.TermsOfServiceController;
import net.eightlives.friendlyssl.factory.AccountBuilderFactory;
import net.eightlives.friendlyssl.factory.RecursiveTimerTaskFactory;
import net.eightlives.friendlyssl.listener.ChallengeTokenRequestedListener;
import net.eightlives.friendlyssl.listener.FriendlySSLApplicationListener;
import net.eightlives.friendlyssl.service.*;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({
        FriendlySSLConfig.class,
        ClockConfig.class,
        TimerConfig.class,
        SchedulerConfig.class,
        RecursiveTimerTaskFactory.class,
        FriendlySSLApplicationListener.class,
        CertificateChallengeController.class,
        CertificateController.class,
        TermsOfServiceController.class,
        AcmeAccountService.class,
        CertificateOrderHandlerService.class,
        CertificateOrderService.class,
        ChallengeProcessorService.class,
        ChallengeTokenRequestedListener.class,
        ChallengeTokenStore.class,
        CSRService.class,
        LocalIdGeneratorService.class,
        PKCS12KeyStoreService.class,
        SSLCertificateCreateRenewService.class,
        TermsOfServiceService.class,
        UpdateCheckerService.class,
        AccountBuilderFactory.class
})
@EnableScheduling
public @interface FriendlySSL {
}
