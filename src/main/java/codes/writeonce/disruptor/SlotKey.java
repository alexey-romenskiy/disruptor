package codes.writeonce.disruptor;

import javax.annotation.Nonnull;

public interface SlotKey<T> {

    T init();

    T clean(@Nonnull T value);
}
