package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class MultiProcessor {

    private final int capacity;

    @Nonnull
    private final MultiBarrier publisherBarrier;

    @Nonnull
    private final Sequence[] barriers;

    @Nonnull
    private final AtomicReference<WaitClient>[] waitListHeads;

    @Nonnull
    private final Sequence sequence;

    @Nonnull
    private final Sequence gatingSequenceCache;

    public MultiProcessor(int capacity, @Nonnull MultiBarrier publisherBarrier, @Nonnull Barrier[] barriers) {
        this.capacity = capacity;
        this.sequence = publisherBarrier.sequence;
        this.publisherBarrier = publisherBarrier;
        this.barriers = Stream.of(barriers).map(e -> e.sequence).toArray(Sequence[]::new);
        this.waitListHeads = toArray(Stream.of(barriers).map(e -> e.waitListHead));
        this.gatingSequenceCache = new Sequence(sequence.get() - capacity);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    private AtomicReference<WaitClient>[] toArray(@Nonnull Stream<AtomicReference<WaitClient>> stream) {
        return stream.toArray(AtomicReference[]::new);
    }

    public long claim(int n) throws InsufficientCapacityException {

        if (n < 1) {
            throw new IllegalArgumentException();
        }

        if (n > capacity) {
            throw new IllegalArgumentException();
        }

        while (true) {
            final var base = sequence.get();
            hasAvailableCapacity(base, n);
            if (sequence.compareAndSet(base, base + n)) {
                return base;
            }
        }
    }

    private void hasAvailableCapacity(long base, int n) throws InsufficientCapacityException {

        final var wrapPoint = base + n - capacity;
        final var cachedGatingSequence = gatingSequenceCache.get();

        if (wrapPoint <= cachedGatingSequence && cachedGatingSequence <= base) {
            return;
        }

        final var minSequence = getMinimumSequence(base);
        gatingSequenceCache.set(minSequence);
        if (wrapPoint > minSequence) {
            throw InsufficientCapacityException.INSTANCE;
        }
    }

    private long getMinimumSequence(long sequence) {

        for (final var barrier : barriers) {
            sequence = Math.min(sequence, barrier.get());
        }

        return sequence;
    }

    public long claimBlocking(int n) throws InsufficientCapacityException {

        if (n < 1) {
            throw new IllegalArgumentException();
        }

        if (n > capacity) {
            throw new IllegalArgumentException();
        }

        while (true) {
            final var base = sequence.get();
            hasAvailableCapacityBlocking(base, n);
            if (sequence.compareAndSet(base, base + n)) {
                return base;
            }
        }
    }

    private void hasAvailableCapacityBlocking(long base, int n) throws InsufficientCapacityException {

        final var wrapPoint = base + n - capacity;
        final var cachedGatingSequence = gatingSequenceCache.get();

        if (wrapPoint <= cachedGatingSequence && cachedGatingSequence <= base) {
            return;
        }

        gatingSequenceCache.set(getMinimumSequenceBlocking(base, wrapPoint));
    }

    private long getMinimumSequenceBlocking(long sequence, long wrapPoint) throws InsufficientCapacityException {

        for (int i = 0, n = barriers.length; i < n; i++) {
            final var barrier = barriers[i];
            var value = barrier.get();
            if (wrapPoint > value) {
                new WaitClient(Thread.currentThread()).await(waitListHeads[i]);
                value = barrier.get();
                if (wrapPoint > value) {
                    throw InsufficientCapacityException.INSTANCE;
                }
            }
            sequence = Math.min(sequence, value);
        }

        return sequence;
    }

    /**
     * Must be called for each processed sequence.
     *
     * @param sequence sequence to publish
     */
    public void publish(long sequence) {
        publisherBarrier.publish(sequence);
    }
}
