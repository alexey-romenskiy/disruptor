package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static codes.writeonce.concurrency.HiveBee.CANCELED;
import static codes.writeonce.concurrency.HiveBee.CLOSED;
import static codes.writeonce.concurrency.HiveBee.IDLE;
import static codes.writeonce.concurrency.HiveBee.WORKING;

public abstract class Hive<V, H extends Hive<V, H>> implements AutoCloseable {

    final IdentityHashMap<Bloom, HiveBee<V, H>> bees = new IdentityHashMap<>();

    private final AtomicReference<HiveBee<V, H>> head = new AtomicReference<>();

    private HiveBee<V, H> readyTail;

    @Nonnull
    public HiveBee<V, H> bee(@Nonnull Bloom bloom, V value) {

        var bee = bees.get(bloom);
        if (bee == null) {
            bee = newBee(bloom, value);
            bees.put(bloom, bee);
        } else {
            switch (bee.state) {
                case IDLE, WORKING -> {
                    // empty
                }
                case CANCELED -> bee.state = WORKING;
                case CLOSED -> throw new IllegalStateException("Illegal state: " + bee.state);
                default -> throw new IllegalStateException("Illegal state: " + bee.state);
            }
        }
        return bee;
    }

    public void select() {

        if (pending()) {
            return;
        }

        doSelect();
    }

    public void select(long nanos) {

        if (pending()) {
            return;
        }

        doSelect(nanos);
    }

    @Override
    public void close() {

        for (final var bee : new ArrayList<>(bees.values())) {
            bee.close();
        }
    }

    protected void addReady(@Nonnull HiveBee<V, H> bee) {

        var next = head.get();
        while (true) {
            bee.nextFree = next;
            final var w = head.compareAndExchange(next, bee);
            if (w == next) {
                break;
            }
            next = w;
        }
    }

    private boolean pending() {
        return readyTail != null || head.get() != null;
    }

    @Nullable
    public HiveBee<V, H> nextBee() {

        final var i = readyTail;
        if (i == null) {
            var t = head.getAndSet(null);
            while (true) {
                if (t == null) {
                    return null;
                }
                if (!checkCancel(t)) {
                    break;
                }
                t = nextAfterCanceled(t);
            }
            var h = t.nextFree;
            while (true) {
                if (h == null) {
                    idle(t);
                    return t;
                }
                if (!checkCancel(h)) {
                    break;
                }
                h = nextAfterCanceled(h);
            }
            readyTail = t;
            var n = h.nextFree;
            while (true) {
                if (n == null) {
                    t.nextFree = t;
                    idle(h);
                    return h;
                }
                if (!checkCancel(n)) {
                    break;
                }
                n = nextAfterCanceled(n);
            }
            h.nextFree = t;
            var p = h;
            while (true) {
                h = n;
                n = n.nextFree;
                while (true) {
                    if (n == null) {
                        t.nextFree = p;
                        idle(h);
                        return h;
                    }
                    if (!checkCancel(n)) {
                        break;
                    }
                    n = nextAfterCanceled(n);
                }
                h.nextFree = p;
                p = h;
            }
        } else {
            var h = i.nextFree;
            while (true) {
                if (h == i) {
                    readyTail = null;
                    h.nextFree = null;
                    if (checkCancel(h)) {
                        return null;
                    }
                    idle(h);
                    return h;
                }
                final var z = h.nextFree;
                h.nextFree = null;
                if (!checkCancel(h)) {
                    i.nextFree = z;
                    idle(h);
                    return h;
                }
                h = z;
            }
        }
    }

    private boolean checkCancel(@Nonnull HiveBee<V, H> bee) {

        switch (bee.state) {
            case WORKING -> {
                return false;
            }
            case CANCELED -> {
                bee.state = CLOSED;
                //noinspection resource
                bees.remove(bee.bloom);
                return true;
            }
            case IDLE, CLOSED -> throw new IllegalStateException("Illegal state: " + bee.state);
            default -> throw new IllegalStateException("Illegal state: " + bee.state);
        }
    }

    private static <V, H extends Hive<V, H>> void idle(@Nonnull HiveBee<V, H> bee) {
        bee.state = IDLE;
    }

    @Nullable
    private static <V, H extends Hive<V, H>> HiveBee<V, H> nextAfterCanceled(@Nonnull HiveBee<V, H> n) {

        final var z = n.nextFree;
        n.nextFree = null;
        return z;
    }

    @Nonnull
    public abstract HiveBee<V, H> newBee(@Nonnull Bloom bloom, V value);

    public abstract void wakeup();

    protected abstract void doSelect();

    protected abstract void doSelect(long nanos);
}
