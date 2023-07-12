package codes.writeonce.concurrency;

import javax.annotation.Nonnull;

public class RunnableBee extends Bee {

    @Nonnull
    private final Runnable runnable;

    public RunnableBee(@Nonnull Bloom bloom, @Nonnull Runnable runnable) {
        super(bloom);
        this.runnable = runnable;
    }

    @Override
    void accept() {
        runnable.run();
    }
}
