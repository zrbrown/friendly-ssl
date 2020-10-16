package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.UpdateFailedException;
import org.shredzone.acme4j.AcmeJsonResource;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeRetryAfterException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class UpdateCheckerService {

    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    public UpdateCheckerService(@Qualifier("update-checker-scheduler") ScheduledExecutorService scheduler,
                                Clock clock) {
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /**
     * Start updating the given resource and checking its status at the returned intervals until it receives a valid
     * or invalid status, or an exception occurs.
     *
     * @param resource the resource to update
     * @return a {@link ScheduledFuture} that:
     * <p>
     * - completes normally when a valid status is returned from the resource update
     * </p>
     * <p>
     * - completes exceptionally when an invalid status is returned from the resource update or an unrecoverable
     * exception occurs when updating the resource
     * </p>
     * @throws UpdateFailedException when the first resource update causes an unrecoverable exception
     */
    public ScheduledFuture<Void> start(AcmeJsonResource resource) {
        long millisecondsUntilRetry = updateAcmeJsonResource(resource);
        return (ScheduledFuture<Void>) scheduler.schedule(() -> {
            while (true) {
                try {
                    Status status = resource.getJSON().get("status").asStatus();
                    switch (status) {
                        case VALID:
                            log.info("Resource is valid");
                            return;
                        case INVALID:
                            log.error("Resource is invalid");
                            throw new UpdateFailedException();
                        default:
                            log.info("Resource status is " + status + ". Updating...");
                            Thread.sleep(updateAcmeJsonResource(resource));
                            break;
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }, millisecondsUntilRetry, TimeUnit.MILLISECONDS);
    }

    private long updateAcmeJsonResource(AcmeJsonResource resource) {
        try {
            resource.update();
            return 0;
        } catch (AcmeRetryAfterException e) {
            return clock.instant().until(e.getRetryAfter(), ChronoUnit.MILLIS);
        } catch (AcmeException e) {
            throw new UpdateFailedException();
        }
    }
}
