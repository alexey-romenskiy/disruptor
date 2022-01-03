package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

public final class Barrier {

    @Nonnull
    final Sequence sequence;

    @Nonnull
    final AtomicReference<WaitClient> waitListHead;

    public Barrier(long sequence, @Nonnull Disruptor disruptor) {
        this.sequence = new Sequence(sequence);
        waitListHead = new AtomicReference<>();
        disruptor.addWaitList(waitListHead);
    }
}
