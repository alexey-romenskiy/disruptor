package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

public class QueueSender<T> implements EventSender<T> {

    @Nonnull
    private final ConcurrentLinkedQueue<T> queuedEvents;

    @Nonnull
    private final Thread thread;

    public QueueSender(
            @Nonnull ConcurrentLinkedQueue<T> queuedEvents,
            @Nonnull DisruptorThread thread
    ) {
        this.queuedEvents = queuedEvents;
        this.thread = thread.getThread();
    }

    @Override
    public void send(long incomingNanos, @Nonnull T event) {

        queuedEvents.add(event);
        LockSupport.unpark(thread);
    }
}
