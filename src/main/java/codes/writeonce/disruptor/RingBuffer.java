package codes.writeonce.disruptor;

import javax.annotation.Nonnull;

public final class RingBuffer<T> {

    @Nonnull
    private final Disruptor disruptor;

    private final int capacityBits;

    private final int capacity;

    private final int mask;

    @Nonnull
    private final Object[] buffer;

    public RingBuffer(@Nonnull Disruptor disruptor, int capacityBits, @Nonnull RingBufferEntryFactory<T> entryFactory) {

        this.capacity = 1 << capacityBits;
        this.capacityBits = capacityBits;
        this.disruptor = disruptor;
        this.mask = capacity - 1;
        this.buffer = new Object[capacity];
        for (int i = 0; i < capacity; i++) {
            this.buffer[i] = entryFactory.newEntry();
        }
    }

    @Nonnull
    public Barrier newBarrier(long sequence) {
        return new Barrier(sequence, disruptor);
    }

    @Nonnull
    public MultiBarrier newMultiBarrier(long sequence) {
        return new MultiBarrier(sequence, capacityBits, capacity, mask, disruptor);
    }

    @Nonnull
    public Processor newProcessor(@Nonnull DisruptorThread thread, @Nonnull Barrier publisherBarrier,
            @Nonnull Barrier... barriers) {
        return new Processor(publisherBarrier, barriers, thread.getThread());
    }

    @Nonnull
    public MultiProcessor newMultiProcessor(@Nonnull MultiBarrier publisherBarrier, @Nonnull Barrier... barriers) {
        return new MultiProcessor(capacity, publisherBarrier, barriers);
    }

    @Nonnull
    public PostMultiProcessor newPostMultiProcessor(@Nonnull DisruptorThread thread, @Nonnull Barrier publisherBarrier,
            @Nonnull MultiBarrier barrier) {
        return new PostMultiProcessor(publisherBarrier, barrier, thread.getThread());
    }

    public int capacity() {
        return capacity;
    }

    public int capacityBits() {
        return capacityBits;
    }

    public int mask() {
        return mask;
    }

    @SuppressWarnings("unchecked")
    public T get(long sequence) {
        return (T) buffer[(int) (sequence & mask)];
    }
}
