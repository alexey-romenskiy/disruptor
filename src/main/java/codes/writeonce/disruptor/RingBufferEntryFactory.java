package codes.writeonce.disruptor;

import javax.annotation.Nonnull;

public interface RingBufferEntryFactory<T> {

    @Nonnull
    T newEntry();
}
