package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.util.concurrent.locks.LockSupport;

public class ParkingBee extends Bee {

    @Nonnull
    private final Thread thread;

    public ParkingBee(@Nonnull Bloom bloom, @Nonnull Thread thread) {
        super(bloom);
        this.thread = thread;
    }

    @Override
    void accept() {
        LockSupport.unpark(thread);
    }
}
