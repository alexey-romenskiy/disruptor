package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class WaitClient {

    private final AtomicBoolean waiting = new AtomicBoolean();

    @Nonnull
    private final Thread thread;

    @Nullable
    private WaitClient next;

    public static void wakeupAll(@Nonnull AtomicReference<WaitClient> head) {

        var next = head.getAndSet(null);
        while (next != null) {
            next = next.wakeup();
        }
    }

    public WaitClient(@Nonnull Thread thread) {
        this.thread = thread;
    }

    @Nullable
    private WaitClient wakeup() {
        final var n = next;
        next = null;
        if (waiting.getAndSet(false)) {
            LockSupport.unpark(thread);
        }
        return n;
    }

    public void await(@Nonnull AtomicReference<WaitClient> head) {
        if (!waiting.getAndSet(true)) {
            var h = head.get();
            while (true) {
                next = h;
                final var w = head.compareAndExchange(h, this);
                if (w == h) {
                    break;
                }
                h = w;
            }
        }
    }

    public void checkThread() {

        if (thread != Thread.currentThread()) {
            throw new IllegalStateException();
        }
    }
}
