package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.exception.UpdateFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shredzone.acme4j.AcmeJsonResource;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeRetryAfterException;
import org.shredzone.acme4j.toolbox.JSON;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateCheckerServiceTest {

    private static final Instant FIXED_CLOCK = Instant.from(OffsetDateTime.of(2020, 2, 3, 4, 5, 6, 0, ZoneOffset.UTC));
    private static final JSON VALID_STATUS_JSON = JSON.parse("{\"status\" : \"VALID\"}");
    ;
    private static final JSON INVALID_STATUS_JSON = JSON.parse("{\"status\" : \"INVALID\"}");
    ;
    private static final JSON PENDING_STATUS_JSON = JSON.parse("{\"status\" : \"PENDING\"}");
    ;

    private UpdateCheckerService service;

    @Mock
    private AcmeJsonResource resource;

    private final TaskScheduler scheduler = new ConcurrentTaskScheduler();

    @DisplayName("Scheduler delay tests")
    @Nested
    class SchedulerDelay {

        @DisplayName("Resource updates successfully")
        @Test
        void updateSuccess() {
            TaskScheduler mockScheduler = mock(TaskScheduler.class);
            service = new UpdateCheckerService(mockScheduler, Clock.fixed(FIXED_CLOCK, ZoneId.of("UTC")));
            service.start(resource);

            verify(mockScheduler, times(1)).schedule(any(Runnable.class), eq(FIXED_CLOCK));
        }

        @DisplayName("Resource update returns a retry time")
        @Test
        void updateRetry() throws AcmeException {
            doThrow(new AcmeRetryAfterException("", FIXED_CLOCK.plus(30, ChronoUnit.SECONDS)))
                    .when(resource).update();

            TaskScheduler mockScheduler = mock(TaskScheduler.class);
            service = new UpdateCheckerService(mockScheduler, Clock.fixed(FIXED_CLOCK, ZoneId.of("UTC")));
            service.start(resource);

            verify(mockScheduler, times(1)).schedule(any(Runnable.class), eq(FIXED_CLOCK.plus(30, ChronoUnit.SECONDS)));
        }
    }

    @DisplayName("Real scheduler tests")
    @Nested
    class RealScheduler {

        private final Clock clock = Clock.systemUTC();

        @BeforeEach
        void setUp() {
            service = new UpdateCheckerService(scheduler, clock);
        }

        @DisplayName("When UpdateFailedException occurs")
        @Test
        void updateFailedException() throws AcmeException {
            doThrow(new UpdateFailedException()).when(resource).update();

            assertThrows(UpdateFailedException.class, () -> service.start(resource));
        }

        @DisplayName("Resource updates with invalid status on first try")
        @Test
        void invalidFirstTry() throws AcmeException {
            when(resource.getJSON()).thenReturn(INVALID_STATUS_JSON);

            ScheduledFuture<Void> future = service.start(resource);

            verify(resource, times(1)).update();

            assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        }

        @DisplayName("Resource updates with valid status on first try")
        @Test
        void validFirstTry() throws AcmeException, InterruptedException, ExecutionException, TimeoutException {
            when(resource.getJSON()).thenReturn(VALID_STATUS_JSON);

            ScheduledFuture<Void> future = service.start(resource);

            verify(resource, times(1)).update();

            future.get(1, TimeUnit.SECONDS);

            assertTrue(future.isDone());
        }

        @DisplayName("Resource updates with pending status then invalid status")
        @Test
        void pendingThenInvalid() throws AcmeException {
            when(resource.getJSON())
                    .thenReturn(PENDING_STATUS_JSON)
                    .thenReturn(INVALID_STATUS_JSON);

            ScheduledFuture<Void> future = service.start(resource);

            assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));

            verify(resource, times(2)).update();
        }

        @DisplayName("Resource updates with pending status then valid status")
        @Test
        void pendingThenValid() throws AcmeException, InterruptedException, ExecutionException, TimeoutException {
            when(resource.getJSON())
                    .thenReturn(PENDING_STATUS_JSON)
                    .thenReturn(VALID_STATUS_JSON);

            ScheduledFuture<Void> future = service.start(resource);

            future.get(1, TimeUnit.SECONDS);

            assertTrue(future.isDone());
            verify(resource, times(2)).update();
        }

        @DisplayName("Resource updates with a retry time in the past")
        @Test
        void retryInPast() throws AcmeException {
            doThrow(new AcmeRetryAfterException("", clock.instant().minus(1, ChronoUnit.SECONDS)))
                    .when(resource).update();
            when(resource.getJSON()).thenReturn(PENDING_STATUS_JSON);

            ScheduledFuture<Void> future = service.start(resource);

            assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));

            verify(resource, times(2)).update();
        }

        @DisplayName("Retry delays")
        @Nested
        class RetryDelays {

            @BeforeEach
            void setUp() throws AcmeException {
                doAnswer(invocation -> null)
                        .doThrow(new AcmeRetryAfterException("", clock.instant().plus(1, ChronoUnit.SECONDS)))
                        .doThrow(new AcmeRetryAfterException("", clock.instant().plus(2, ChronoUnit.SECONDS)))
                        .doThrow(new AcmeRetryAfterException("", clock.instant().plus(3, ChronoUnit.SECONDS)))
                        .when(resource).update();
            }

            @DisplayName("Resource updates with a few retry delays then invalid status")
            @Test
            void retriesThenInvalid() throws AcmeException {
                when(resource.getJSON())
                        .thenReturn(PENDING_STATUS_JSON)
                        .thenReturn(PENDING_STATUS_JSON)
                        .thenReturn(PENDING_STATUS_JSON)
                        .thenReturn(INVALID_STATUS_JSON);

                ScheduledFuture<Void> future = service.start(resource);

                assertThrows(ExecutionException.class, () -> future.get(7, TimeUnit.SECONDS));

                verify(resource, times(4)).update();
            }

            @DisplayName("Resource updates with a few retry delays then valid status")
            @Test
            void retriesThenValid() throws AcmeException, InterruptedException, ExecutionException, TimeoutException {
                when(resource.getJSON())
                        .thenReturn(PENDING_STATUS_JSON)
                        .thenReturn(PENDING_STATUS_JSON)
                        .thenReturn(PENDING_STATUS_JSON)
                        .thenReturn(VALID_STATUS_JSON);

                ScheduledFuture<Void> future = service.start(resource);

                future.get(7, TimeUnit.SECONDS);

                assertTrue(future.isDone());
                verify(resource, times(4)).update();
            }
        }
    }
}
