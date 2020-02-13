package net.eightlives.friendlyssl.annotation;

import net.eightlives.friendlyssl.controller.CertificateChallengeController;
import net.eightlives.friendlyssl.service.*;
import net.eightlives.friendlyssl.startup.SSLStartupRunner;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({
        CertificateChallengeController.class,
        AcmeAccountService.class,
        CertificateOrderService.class,
        ChallengeProcessorService.class,
        ChallengeTokenRequestedListenerService.class,
        ChallengeTokenStore.class,
        CSRService.class,
        PKCS12KeyStoreGeneratorService.class,
        UpdateCheckerService.class,
        SSLStartupRunner.class
})
public @interface FriendlySSL {
}
