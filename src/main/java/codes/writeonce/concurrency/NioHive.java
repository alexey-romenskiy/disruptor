package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Selector;

public class NioHive<V> extends Hive<V, NioHive<V>> {

    @Nonnull
    final Selector selector;

    public NioHive(@Nonnull Selector selector) {
        this.selector = selector;
    }

    @Override
    @Nonnull
    public NioHiveBee<V> newBee(@Nonnull Bloom bloom, V value) {
        return new NioHiveBee<>(this, bloom, value);
    }

    @Override
    public void wakeup() {
        selector.wakeup();
    }

    @Override
    protected void doSelect() {

        try {
            selector.select();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void doSelect(long nanos) {

        if (nanos <= 0) {
            return;
        }

        try {
            selector.select((nanos + 999_999) / 1_000_000);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
