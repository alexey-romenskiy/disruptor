package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.nio.channels.Selector;

public class NioLedgerSelector<T> extends LedgerSelector<T, NioLedgerSelector<T>> {

    @Nonnull
    final Selector selector;

    public NioLedgerSelector(@Nonnull Selector selector) {
        this.selector = selector;
    }

    @Override
    @Nonnull
    protected NioLedgerSelectionKey<T> newSelectionKey(@Nonnull WaitableHost host, T value) {
        return new NioLedgerSelectionKey<T>(this, host, value);
    }
}
