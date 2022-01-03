package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class Processor {

    @Nonnull
    private final Sequence publisherSequence;

    @Nonnull
    private final AtomicReference<WaitClient> publisherWaitListHead;

    @Nonnull
    private final Sequence[] sequences;

    @Nonnull
    private final WaitClientWrapper[] waitClients;

    private long sequence;

    public Processor(@Nonnull Barrier publisherBarrier, @Nonnull Barrier[] barriers, @Nonnull Thread thread) {
        this.publisherSequence = publisherBarrier.sequence;
        this.publisherWaitListHead = publisherBarrier.waitListHead;
        this.sequence = publisherSequence.get();
        this.sequences = Stream.of(barriers).map(e -> e.sequence).toArray(Sequence[]::new);
        this.waitClients = Stream.of(barriers).map(e -> new WaitClientWrapper(e.waitListHead, new WaitClient(thread)))
                .toArray(WaitClientWrapper[]::new);
    }

    public int claim() {

        final var count = getMinCount();
        if (count > 0) {
            sequence += count;
        }
        return (int) count;
    }

    private long getMinCount() {

        var min = sequences[0].get() - sequence;
        for (int i = 1, n = sequences.length; i < n; i++) {
            min = Math.min(min, sequences[i].get() - sequence);
        }

        return min;
    }

    public int claimBlocking() {

        final var count = getMinCountBlocking();
        if (count > 0) {
            sequence += count;
        }
        return (int) count;
    }

    private long getMinCountBlocking() {

        var min = sequences[0].get() - sequence;
        if (min == 0) {
            waitClients[0].await();
            min = sequences[0].get() - sequence;
            if (min == 0) {
                return min;
            }
        }

        for (int i = 1, n = sequences.length; i < n; i++) {
            var value = sequences[i].get() - sequence;
            if (value == 0) {
                waitClients[i].await();
                value = sequences[i].get() - sequence;
                if (value == 0) {
                    return value;
                }
            }
            min = Math.min(min, value);
        }

        return min;
    }

    /**
     * @param sequence first unpublished sequence
     */
    public void publish(long sequence) {
        publisherSequence.set(sequence);
        WaitClient.wakeupAll(publisherWaitListHead);
    }
}
