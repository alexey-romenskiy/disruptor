package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicReference;

public abstract class LedgerSelector<T, S extends LedgerSelector<T, S>> implements AutoCloseable {

    final IdentityHashMap<WaitableHost, LedgerSelectionKey<T, S>> selectionKeys = new IdentityHashMap<>();

    private final AtomicReference<LedgerSelectionKey<T, S>> head = new AtomicReference<>();

    private LedgerSelectionKey<T, S> readyTail;

    @Nonnull
    public LedgerSelectionKey<T, S> selectionKey(
            @Nonnull WaitableHost host,
            T value
    ) {
        var selectionKey = selectionKeys.get(host);
        if (selectionKey == null) {
            selectionKey = newSelectionKey(host, value);
            selectionKeys.put(host, selectionKey);
        } else {
            selectionKey.value(value);
        }
        return selectionKey;
    }

    @Override
    public void close() {

        for (final var selectionKey : selectionKeys.values()) {
            selectionKey.canceled = true;
        }

        selectionKeys.clear();
    }

    protected void addReady(@Nonnull LedgerSelectionKey<T, S> item) {

        var next = head.get();
        while (true) {
            item.nextReady = next;
            final var w = head.compareAndExchange(next, item);
            if (w == next) {
                break;
            }
            next = w;
        }
    }

    @Nullable
    public LedgerSelectionKey<T, S> nextReady() {

        final var i = readyTail;
        if (i == null) {
            var t = head.getAndSet(null);
            while (true) {
                if (t == null) {
                    return null;
                }
                if (!t.canceled) {
                    break;
                }
                t = nextAfterCanceled(t);
            }
            var h = t.nextReady;
            while (true) {
                if (h == null) {
                    return t;
                }
                if (!h.canceled) {
                    break;
                }
                h = nextAfterCanceled(h);
            }
            readyTail = t;
            var n = h.nextReady;
            while (true) {
                if (n == null) {
                    t.nextReady = t;
                    return h;
                }
                if (!n.canceled) {
                    break;
                }
                n = nextAfterCanceled(n);
            }
            h.nextReady = t;
            var p = h;
            while (true) {
                h = n;
                n = n.nextReady;
                while (true) {
                    if (n == null) {
                        t.nextReady = p;
                        return h;
                    }
                    if (!n.canceled) {
                        break;
                    }
                    n = nextAfterCanceled(n);
                }
                h.nextReady = p;
                p = h;
            }
        } else {
            var h = i.nextReady;
            while (true) {
                if (h == i) {
                    readyTail = null;
                    h.nextReady = null;
                    if (h.canceled) {
                        return null;
                    }
                    return h;
                }
                final var z = h.nextReady;
                h.nextReady = null;
                if (!h.canceled) {
                    i.nextReady = z;
                    return h;
                }
                h = z;
            }
        }
    }

    @Nullable
    private LedgerSelectionKey<T, S> nextAfterCanceled(@Nonnull LedgerSelectionKey<T, S> n) {

        final var z = n.nextReady;
        n.nextReady = null;
        return z;
    }

    @Nonnull
    protected abstract LedgerSelectionKey<T, S> newSelectionKey(@Nonnull WaitableHost host, T value);
}
