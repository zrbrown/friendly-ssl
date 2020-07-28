package net.eightlives.friendlyssl.factory;

import net.eightlives.friendlyssl.task.RecursiveTimerTask;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

@Component
public class RecursiveTimerTaskFactory {

    private final Clock clock;

    public RecursiveTimerTaskFactory(Clock clock) {
        this.clock = clock;
    }

    public RecursiveTimerTask create(ScheduledExecutorService timer, Supplier<Instant> task) {
        return new RecursiveTimerTask(timer, task, clock);
    }
}
