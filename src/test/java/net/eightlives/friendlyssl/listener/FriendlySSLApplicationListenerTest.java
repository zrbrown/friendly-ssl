package net.eightlives.friendlyssl.listener;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import net.eightlives.friendlyssl.service.SSLCertificateCreateRenewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.security.Security;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendlySSLApplicationListenerTest {

    private FriendlySSLApplicationListener listener;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private SSLCertificateCreateRenewService createRenewService;

    @BeforeEach
    void setUp() {
        listener = new FriendlySSLApplicationListener(config, createRenewService);
    }

    @DisplayName("Testing that security provider is correctly set")
    @Test
    void onApplicationEventSetsProvider() {
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        listener.onApplicationEvent(event);

        assertNotNull(Security.getProvider("BC"));
    }

    @DisplayName("Testing that create or renew service is not called when auto renew is disabled")
    @Test
    void onApplicationEventConfigDisabled() {
        when(config.isAutoRenewEnabled()).thenReturn(false);

        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        listener.onApplicationEvent(event);

        verify(createRenewService, times(0)).createOrRenew();
    }

    @DisplayName("Testing that create or renew service is correctly scheduled when auto renew is enabled")
    @Test
    @Timeout(value = 2)
    void onApplicationEventConfigEnabled() throws InterruptedException {
        when(config.isAutoRenewEnabled()).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(5);
        when(createRenewService.createOrRenew()).thenAnswer(invocation -> {
            latch.countDown();
            return new CertificateRenewal(CertificateRenewalStatus.SUCCESS,
                    Instant.now().plus(200, ChronoUnit.MILLIS));
        });

        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        listener.onApplicationEvent(event);

        latch.await();

        verify(createRenewService, times(5)).createOrRenew();
    }
}