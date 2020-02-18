package net.eightlives.friendlyssl.task;

import java.time.Instant;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Supplier;

public class RecursiveTimerTask extends TimerTask {

    private final Timer timer;
    private final Supplier<Instant> task;

    public RecursiveTimerTask(Timer timer, Supplier<Instant> task) {
        this.timer = timer;
        this.task = task;
    }

    @Override
    public void run() {
        Instant renewTime = task.get();
        timer.schedule(new RecursiveTimerTask(timer, task), Date.from(renewTime));
    }
}
