package codes.writeonce.disruptor;

import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

public final class MultiBarrier {

    private static final Unsafe UNSAFE = Utils.getUnsafe();
    private static final long BASE = UNSAFE.arrayBaseOffset(int[].class);
    private static final long SCALE = UNSAFE.arrayIndexScale(int[].class);

    private final int[] availableBuffer;

    private final int capacityBits;

    private final int mask;

    @Nonnull
    final Sequence sequence;

    @Nonnull
    final AtomicReference<WaitClient> waitListHead;

    public MultiBarrier(long sequence, int capacityBits, int capacity, int mask, @Nonnull Disruptor disruptor) {
        this.availableBuffer = new int[capacity];
        this.capacityBits = capacityBits;
        this.mask = mask;
        this.sequence = new Sequence(sequence);
        initialiseAvailableBuffer();
        waitListHead = new AtomicReference<>();
        disruptor.addWaitList(waitListHead);
    }

    void publish(long sequence) {

        final var index = calculateIndex(sequence);
        final var flag = calculateAvailabilityFlag(sequence);

        setAvailableBufferValue(index, flag);

        WaitClient.wakeupAll(waitListHead);
    }

    boolean isAvailable(long sequence) {

        final var index = calculateIndex(sequence);
        final var flag = calculateAvailabilityFlag(sequence);
        final var bufferAddress = index * SCALE + BASE;
        return UNSAFE.getIntVolatile(availableBuffer, bufferAddress) == flag;
    }

    private void setAvailableBufferValue(int index, int flag) {

        final var bufferAddress = index * SCALE + BASE;
        UNSAFE.putIntVolatile(availableBuffer, bufferAddress, flag);
    }

    private int calculateAvailabilityFlag(final long sequence) {
        return (int) (sequence >>> capacityBits);
    }

    private int calculateIndex(final long sequence) {
        return ((int) sequence) & mask;
    }

    private void initialiseAvailableBuffer() {

        for (int i = availableBuffer.length - 1; i != 0; i--) {
            setAvailableBufferValue(i, -1);
        }

        setAvailableBufferValue(0, -1);
    }
}
