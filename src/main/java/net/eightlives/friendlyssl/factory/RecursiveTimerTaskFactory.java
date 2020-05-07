package net.eightlives.friendlyssl.factory;

import net.eightlives.friendlyssl.task.RecursiveTimerTask;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Timer;
import java.util.function.Supplier;

@Component
public class RecursiveTimerTaskFactory {

    public RecursiveTimerTask create(Timer timer, Supplier<Instant> task) {
        return new RecursiveTimerTask(timer, task);
    }
}
