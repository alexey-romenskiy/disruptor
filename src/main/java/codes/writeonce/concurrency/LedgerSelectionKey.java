package codes.writeonce.concurrency;

import javax.annotation.Nonnull;

public abstract class LedgerSelectionKey<T, S extends LedgerSelector<T, S>> extends Waitable implements AutoCloseable {

    @Nonnull
    protected final S selector;

    LedgerSelectionKey<T, S> nextReady;

    boolean canceled;

    private T value;

    public LedgerSelectionKey(
            @Nonnull S selector,
            @Nonnull WaitableHost host,
            T value
    ) {
        super(host);
        this.selector = selector;
        this.value = value;
    }

    public T value() {
        return value;
    }

    public void value(T value) {
        this.value = value;
    }

    @Override
    public void close() {

        if (canceled) {
            return;
        }

        canceled = true;

        //noinspection resource
        selector.selectionKeys.remove(host);
    }

    @Override
    void accept() {
        selector.addReady(this);
    }
}
