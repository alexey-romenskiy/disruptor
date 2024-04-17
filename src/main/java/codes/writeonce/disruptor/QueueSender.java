package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class QueueSender<T> implements EventSender<T> {

    @Nonnull
    private final ConcurrentLinkedQueue<Wrapper<T>> queuedEvents;

    @Nonnull
    private final Thread thread;

    public QueueSender(
            @Nonnull ConcurrentLinkedQueue<Wrapper<T>> queuedEvents,
            @Nonnull DisruptorThread thread
    ) {
        this.queuedEvents = queuedEvents;
        this.thread = thread.getThread();
    }

    @Override
    public void send(long incomingNanos, @Nonnull T event) {

        queuedEvents.add(new Wrapper<>(event, null));
        LockSupport.unpark(thread);
    }

    public void send(long incomingNanos, @Nonnull T event, @Nullable AtomicLong counter) {

        queuedEvents.add(new Wrapper<>(event, counter));
        LockSupport.unpark(thread);
    }

    public record Wrapper<T>(@Nonnull T event, @Nullable AtomicLong counter) {
        // empty
    }
}
