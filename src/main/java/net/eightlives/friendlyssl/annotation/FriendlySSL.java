package net.eightlives.friendlyssl.annotation;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.controller.CertificateChallengeController;
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
        UpdateCheckerService.class
})
public @interface FriendlySSL {
}
