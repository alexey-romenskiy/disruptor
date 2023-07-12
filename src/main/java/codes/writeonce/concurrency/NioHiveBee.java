package codes.writeonce.concurrency;

import javax.annotation.Nonnull;

public class NioHiveBee<V> extends HiveBee<V, NioHive<V>> {

    public NioHiveBee(
            @Nonnull NioHive<V> hive,
            @Nonnull Bloom bloom,
            V value
    ) {
        super(hive, bloom, value);
    }

    @Override
    void accept() {
        super.accept();
        hive.selector.wakeup();
    }
}
