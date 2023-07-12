package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class HiveTimer {

    private final AtomicBoolean locked = new AtomicBoolean();

    @Nonnull
    private final Hive<?, ?> hive;

    private boolean scheduled;

    private boolean waiting;

    private long nanos;

    public HiveTimer(@Nonnull Hive<?, ?> hive) {
        this.hive = hive;
    }

    /**
     * @return <code>true</code> if timer triggered
     */
    public boolean select() throws IOException {

        final long delay;
        final boolean infinite;

        lock();
        try {
            if (scheduled) {
                delay = nanos - System.nanoTime();
                if (delay <= 0) {
                    scheduled = false;
                    return true;
                }
                infinite = false;
            } else {
                delay = 0;
                infinite = true;
            }
            waiting = true;
        } finally {
            unlock();
        }

        if (infinite) {
            hive.select();
        } else {
            hive.select(delay);
        }

        lock();
        try {
            waiting = false;
            if (scheduled && nanos - System.nanoTime() <= 0) {
                scheduled = false;
                return true;
            } else {
                return false;
            }
        } finally {
            unlock();
        }
    }

    public void schedule(long nanos) {

        lock();
        try {
            if (scheduled) {
                if (this.nanos - nanos > 0) {
                    this.nanos = nanos;
                    if (!waiting) {
                        return;
                    }
                } else {
                    this.nanos = nanos;
                    return;
                }
            } else {
                this.nanos = nanos;
                scheduled = true;
                if (!waiting) {
                    return;
                }
            }
        } finally {
            unlock();
        }

        hive.wakeup();
    }

    public void cancel() {

        lock();
        try {
            scheduled = false;
        } finally {
            unlock();
        }
    }

    private void lock() {

        while (true) {
            if (locked.compareAndSet(false, true)) {
                break;
            }
        }
    }

    private void unlock() {
        locked.set(false);
    }
}
