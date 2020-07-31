package net.eightlives.friendlyssl.listener;

import net.eightlives.friendlyssl.config.FriendlySSLConfig;
import net.eightlives.friendlyssl.factory.RecursiveTimerTaskFactory;
import net.eightlives.friendlyssl.model.CertificateRenewal;
import net.eightlives.friendlyssl.model.CertificateRenewalStatus;
import net.eightlives.friendlyssl.service.AutoRenewService;
import net.eightlives.friendlyssl.task.RecursiveTimerTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.security.Security;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendlySSLApplicationListenerTest {

    private FriendlySSLApplicationListener listener;

    @Mock
    private FriendlySSLConfig config;
    @Mock
    private AutoRenewService autoRenewService;
    @Mock
    private RecursiveTimerTaskFactory timerTaskFactory;
    @Mock
    private ScheduledExecutorService timer;

    @BeforeEach
    void setUp() {
        listener = new FriendlySSLApplicationListener(config, autoRenewService, timerTaskFactory, timer);
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

        verify(timer, times(0)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.SECONDS));
        verify(autoRenewService, times(0)).autoRenew();
    }

    @DisplayName("Testing that create or renew service is correctly scheduled when auto renew is enabled")
    @Test
    void onApplicationEventConfigEnabled() {
        when(config.isAutoRenewEnabled()).thenReturn(true);
        RecursiveTimerTask timerTask = mock(RecursiveTimerTask.class);
        ArgumentCaptor<Supplier<Instant>> createOrRenewSupplier = ArgumentCaptor.forClass(Supplier.class);
        when(timerTaskFactory.create(same(timer), createOrRenewSupplier.capture())).thenReturn(timerTask);
        CertificateRenewal renewal = new CertificateRenewal(CertificateRenewalStatus.SUCCESS,
                Instant.ofEpochMilli(100000));
        when(autoRenewService.autoRenew()).thenReturn(renewal);

        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        listener.onApplicationEvent(event);

        ArgumentCaptor<RecursiveTimerTask> timerTaskArg = ArgumentCaptor.forClass(RecursiveTimerTask.class);
        ArgumentCaptor<Long> secondsArg = ArgumentCaptor.forClass(Long.class);
        verify(timer, times(1)).schedule(timerTaskArg.capture(), secondsArg.capture(), eq(TimeUnit.SECONDS));

        assertEquals(timerTask, timerTaskArg.getValue());
        assertEquals(1, secondsArg.getValue());
        assertEquals(renewal.getTime(), createOrRenewSupplier.getValue().get());
    }
}
