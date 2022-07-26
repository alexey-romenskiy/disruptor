package codes.writeonce.concurrency;

import javax.annotation.Nonnull;

public class NioLedgerSelectionKey<T> extends LedgerSelectionKey<T, NioLedgerSelector<T>> {

    public NioLedgerSelectionKey(
            @Nonnull NioLedgerSelector<T> ledgerSelector,
            @Nonnull WaitableHost host,
            T value
    ) {
        super(ledgerSelector, host, value);
    }

    @Override
    void accept() {
        super.accept();
        selector.selector.wakeup();
    }
}
