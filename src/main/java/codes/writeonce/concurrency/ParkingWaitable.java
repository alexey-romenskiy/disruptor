package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.util.concurrent.locks.LockSupport;

public class ParkingWaitable extends Waitable {

    @Nonnull
    private final Thread thread;

    public ParkingWaitable(@Nonnull WaitableHost host, @Nonnull Thread thread) {
        super(host);
        this.thread = thread;
    }

    @Override
    void accept() {
        LockSupport.unpark(thread);
    }
}
