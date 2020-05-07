package net.eightlives.friendlyssl.annotation;

import net.eightlives.friendlyssl.config.ClockConfig;
import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.config.TimerConfig;
import net.eightlives.friendlyssl.controller.CertificateChallengeController;
import net.eightlives.friendlyssl.factory.AccountBuilderFactory;
import net.eightlives.friendlyssl.factory.RecursiveTimerTaskFactory;
import net.eightlives.friendlyssl.listener.ChallengeTokenRequestedListener;
import net.eightlives.friendlyssl.listener.FriendlySSLApplicationListener;
import net.eightlives.friendlyssl.service.*;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({
        FriendlySSLConfig.class,
        ClockConfig.class,
        TimerConfig.class,
        RecursiveTimerTaskFactory.class,
        FriendlySSLApplicationListener.class,
        CertificateChallengeController.class,
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
public @interface FriendlySSL {
}
