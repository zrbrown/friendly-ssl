package net.eightlives.friendlyssl.task;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RecursiveTimerTask implements Runnable {

    private final ScheduledExecutorService timer;
    private final Supplier<Instant> task;
    private final Clock clock;

    public RecursiveTimerTask(ScheduledExecutorService timer, Supplier<Instant> task, Clock clock) {
        this.timer = timer;
        this.task = task;
        this.clock = clock;
    }

    @Override
    public void run() {
        Instant renewTime = task.get();
        timer.schedule(new RecursiveTimerTask(timer, task, clock),
                Duration.between(clock.instant(), renewTime).get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
    }
}
