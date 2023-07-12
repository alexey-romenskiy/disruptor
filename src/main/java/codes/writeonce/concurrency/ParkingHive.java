package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.util.concurrent.locks.LockSupport;

public class ParkingHive<V> extends Hive<V, ParkingHive<V>> {

    @Nonnull
    final Thread thread;

    public ParkingHive(@Nonnull Thread thread) {
        this.thread = thread;
    }

    @Override
    @Nonnull
    public ParkingHiveBee<V> newBee(@Nonnull Bloom bloom, V value) {
        return new ParkingHiveBee<>(this, bloom, value);
    }

    @Override
    public void wakeup() {
        LockSupport.unpark(thread);
    }

    @Override
    protected void doSelect() {

        LockSupport.park();
    }

    @Override
    protected void doSelect(long nanos) {

        if (nanos <= 0) {
            return;
        }

        LockSupport.parkNanos(nanos);
    }
}
