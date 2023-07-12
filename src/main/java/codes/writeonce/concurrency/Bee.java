package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

public abstract class Bee {

    final AtomicBoolean busy = new AtomicBoolean();

    @Nonnull
    protected final Bloom bloom;

    Bee nextWaiting;

    public Bee(@Nonnull Bloom bloom) {
        this.bloom = requireNonNull(bloom);
    }

    @Nonnull
    public Bloom bloom() {
        return bloom;
    }

    public boolean busy() {
        return busy.get();
    }

    public void ready() {

        if (!busy.getAndSet(true)) {
            var next = bloom.head.get();
            while (true) {
                nextWaiting = next;
                final var w = bloom.head.compareAndExchange(next, this);
                if (w == next) {
                    break;
                }
                next = w;
            }
        }
    }

    abstract void accept();
}
