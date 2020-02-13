package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.UpdateFailedException;
import org.shredzone.acme4j.AcmeJsonResource;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeRetryAfterException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class UpdateCheckerService {

    public ScheduledFuture<?> start(ScheduledExecutorService executor, AcmeJsonResource resource) {
        long millisecondsUntilRetry = updateAcmeJsonResource(resource);
        return executor.schedule(() -> {
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
            return Instant.now().until(e.getRetryAfter(), ChronoUnit.MILLIS);
        } catch (AcmeException e) {
            throw new UpdateFailedException();
        }
    }
}
