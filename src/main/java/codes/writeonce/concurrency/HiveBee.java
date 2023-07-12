package codes.writeonce.concurrency;

import javax.annotation.Nonnull;

public abstract class HiveBee<V, H extends Hive<V, H>> extends Bee implements AutoCloseable {

    static final int IDLE = 0;
    static final int WORKING = 1;
    static final int CANCELED = 2;
    static final int CLOSED = 3;

    @Nonnull
    protected final H hive;

    HiveBee<V, H> nextFree;

    int state = IDLE;

    private V value;

    public HiveBee(
            @Nonnull H hive,
            @Nonnull Bloom bloom,
            V value
    ) {
        super(bloom);
        this.hive = hive;
        this.value = value;
    }

    @Override
    public void ready() {

        switch (state) {
            case IDLE -> {
                state = WORKING;
                super.ready();
            }
            case WORKING -> {
                // empty
            }
            case CANCELED, CLOSED -> throw new IllegalStateException("Illegal state: " + state);
            default -> throw new IllegalStateException("Illegal state: " + state);
        }
    }

    public V value() {
        return value;
    }

    public void value(V value) {
        this.value = value;
    }

    @Override
    public void close() {

        switch (state) {
            case IDLE -> {
                state = CLOSED;
                //noinspection resource
                hive.bees.remove(bloom);
            }
            case WORKING -> state = CANCELED;
            case CANCELED, CLOSED -> {
                // empty
            }
            default -> throw new IllegalStateException("Illegal state: " + state);
        }
    }

    @Override
    void accept() {
        hive.addReady(this);
    }
}
