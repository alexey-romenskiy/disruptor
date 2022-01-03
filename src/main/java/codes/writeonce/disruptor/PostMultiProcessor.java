package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

public final class PostMultiProcessor {

    @Nonnull
    private final Sequence publisherSequence;

    @Nonnull
    private final AtomicReference<WaitClient> publisherWaitListHead;

    @Nonnull
    private final MultiBarrier barrier;

    @Nonnull
    private final Sequence barrierSequence;

    @Nonnull
    private final WaitClientWrapper waitClient;

    private long sequence;

    public PostMultiProcessor(@Nonnull Barrier publisherBarrier, @Nonnull MultiBarrier barrier,
            @Nonnull Thread thread) {
        this.publisherSequence = publisherBarrier.sequence;
        this.publisherWaitListHead = publisherBarrier.waitListHead;
        this.sequence = publisherSequence.get();
        this.barrier = barrier;
        this.barrierSequence = barrier.sequence;
        this.waitClient = new WaitClientWrapper(barrier.waitListHead, new WaitClient(thread));
    }

    public int claim() {

        var count = (int) (barrierSequence.get() - sequence);

        if (count == 0) {
            return 0;
        }

        var i = sequence;
        while (true) {
            if (!barrier.isAvailable(i)) {
                break;
            }
            i++;
            if (--count == 0) {
                break;
            }
        }
        final var n = (int) (i - sequence);
        sequence = i;
        return n;
    }

    public int claimBlocking() {

        var count = (int) (barrierSequence.get() - sequence);

        if (count == 0) {
            waitClient.await();
            count = (int) (barrierSequence.get() - sequence);
            if (count == 0) {
                return 0;
            }
            var i = sequence;
            if (!barrier.isAvailable(i)) {
                return 0;
            }
            i++;
            while (--count != 0 && barrier.isAvailable(i)) {
                i++;
            }
            final var n = (int) (i - sequence);
            sequence = i;
            return n;
        } else {
            var i = sequence;
            if (!barrier.isAvailable(i)) {
                waitClient.await();
                if (!barrier.isAvailable(i)) {
                    return 0;
                }
            }
            i++;
            while (--count != 0 && barrier.isAvailable(i)) {
                i++;
            }
            final var n = (int) (i - sequence);
            sequence = i;
            return n;
        }
    }

    /**
     * @param sequence first unpublished sequence
     */
    public void publish(long sequence) {
        publisherSequence.set(sequence);
        WaitClient.wakeupAll(publisherWaitListHead);
    }
}
