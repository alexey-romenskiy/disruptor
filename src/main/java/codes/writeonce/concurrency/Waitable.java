package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Waitable {

    final AtomicBoolean ready = new AtomicBoolean();

    @Nonnull
    protected final WaitableHost host;

    Waitable nextWaiting;

    public Waitable(@Nonnull WaitableHost host) {
        this.host = host;
    }

    public void register() {
        host.register(this);
    }

    abstract void accept();
}
