package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class MainWorker<T> extends AbstractMainWorker<T> {

    public MainWorker(
            @Nonnull Disruptor disruptor,
            @Nonnull DisruptorThread thread,
            @Nonnull Slots slots,
            @Nonnull Processor innerProcessor,
            @Nonnull PostMultiProcessor externalProcessor,
            @Nonnull RingBuffer<DisruptorEntry<T>> queue1,
            @Nonnull RingBuffer<EventHolder<T>> queue2,
            @Nonnull Supplier<T> timerEventFactory,
            @Nonnull ConcurrentLinkedQueue<QueueSender.Wrapper<T>> queuedEvents
    ) {
        super(disruptor, thread, slots, innerProcessor, externalProcessor, queue1, queue2, timerEventFactory,
                queuedEvents);
    }

    @Override
    protected boolean isShutdownEvent(@Nonnull DisruptorEntry<T> entry) {
        return entry.event instanceof ShutdownEvent;
    }

    @Override
    public void run() {
        mainLoop(0, 0, queue1.capacity(), Instant.now());
    }
}
