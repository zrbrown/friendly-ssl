package net.eightlives.friendlyssl.task;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A {@link Runnable} that will run a given task at self-defined intervals. Every time the task is run, it returns the next
 * time that it should run, runs at that time, returns the next time it should run, and so on.
 */
public class RecursiveTimerTask implements Runnable {

    private final ScheduledExecutorService timer;
    private final Supplier<Instant> task;
    private final Clock clock;

    /**
     * Construct a new {@link RecursiveTimerTask}.
     *
     * @param timer the {@link ScheduledExecutorService} with which to schedule future tasks
     * @param task  the task to {@link Runnable#run()}. It must return an {@link Instant} which will inform {@code timer} of when to next run this task.
     * @param clock the {@link Clock} from which to derive time
     */
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
