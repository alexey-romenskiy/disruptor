package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.util.concurrent.locks.LockSupport;

public class ParkingHiveBee<V> extends HiveBee<V, ParkingHive<V>> {

    public ParkingHiveBee(
            @Nonnull ParkingHive<V> hive,
            @Nonnull Bloom bloom,
            V value
    ) {
        super(hive, bloom, value);
    }

    @Override
    void accept() {
        super.accept();
        LockSupport.unpark(hive.thread);
    }
}
