package codes.writeonce.disruptor;

import sun.misc.Unsafe;

class SequenceLeftPadding {

    @SuppressWarnings("unused")
    protected long padding1, padding2, padding3, padding4, padding5, padding6, padding7;
}

class SequenceFields extends SequenceLeftPadding {

    protected volatile long sequence;

    SequenceFields(long sequence) {
        this.sequence = sequence;
    }
}

public final class Sequence extends SequenceFields {

    private static final Unsafe UNSAFE = Utils.getUnsafe();

    private static final long VALUE;

    static {
        try {
            VALUE = UNSAFE.objectFieldOffset(SequenceFields.class.getDeclaredField("sequence"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unused", "ProtectedMemberInFinalClass"})
    protected long padding9, padding10, padding11, padding12, padding13, padding14, padding15;

    public Sequence(long sequence) {
        super(sequence);
    }

    public long get() {
        return sequence;
    }

    public void set(long sequence) {
        this.sequence = sequence;
    }

    public boolean compareAndSet(long expectedValue, long newValue) {
        return UNSAFE.compareAndSwapLong(this, VALUE, expectedValue, newValue);
    }
}
