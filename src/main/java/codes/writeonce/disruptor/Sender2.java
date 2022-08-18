package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class Sender2<T> {

    @Nonnull
    private final Disruptor disruptor;

    @Nonnull
    private final MultiProcessor processor;

    @Nonnull
    private final RingBuffer<EventHolder2<T>> ringBuffer;

    public Sender2(
            @Nonnull Disruptor disruptor,
            @Nonnull MultiProcessor processor,
            @Nonnull RingBuffer<EventHolder2<T>> ringBuffer
    ) {
        this.disruptor = disruptor;
        this.processor = processor;
        this.ringBuffer = ringBuffer;
    }

    public void sendNonblocking(long incomingNanos, @Nonnull T event) {

        while (true) {
            try {
                final var sequence = processor.claim(1);
                final var holder = ringBuffer.get(sequence);
                holder.incomingNanos = incomingNanos;
                holder.event = event;
                processor.publish(sequence);
                break;
            } catch (InsufficientCapacityException ignore) {
                if (disruptor.isTerminate()) {
                    break;
                }
            }
        }
    }

    public void send(long incomingNanos, @Nonnull T event) {

        while (true) {
            try {
                final var sequence = processor.claimBlocking(1);
                final var holder = ringBuffer.get(sequence);
                holder.incomingNanos = incomingNanos;
                holder.event = event;
                processor.publish(sequence);
                break;
            } catch (InsufficientCapacityException ignore) {
                if (disruptor.isTerminate()) {
                    break;
                }
                LockSupport.park();
            }
        }
    }

    public void send(long incomingNanos, @Nonnull Consumer<EventHolder2<T>> consumer) {

        while (true) {
            try {
                final var sequence = processor.claimBlocking(1);
                final var holder = ringBuffer.get(sequence);
                holder.incomingNanos = incomingNanos;
                consumer.accept(holder);
                processor.publish(sequence);
                break;
            } catch (InsufficientCapacityException ignore) {
                if (disruptor.isTerminate()) {
                    break;
                }
                LockSupport.park();
            }
        }
    }
}
