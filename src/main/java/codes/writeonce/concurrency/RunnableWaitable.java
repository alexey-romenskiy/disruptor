package codes.writeonce.concurrency;

import javax.annotation.Nonnull;

public class RunnableWaitable extends Waitable {

    @Nonnull
    private final Runnable runnable;

    public RunnableWaitable(@Nonnull WaitableHost host, @Nonnull Runnable runnable) {
        super(host);
        this.runnable = runnable;
    }

    @Override
    void accept() {
        runnable.run();
    }
}
