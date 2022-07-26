package codes.writeonce.concurrency;

import javax.annotation.Nonnull;

public class ParkingLedgerSelector<T> extends LedgerSelector<T, ParkingLedgerSelector<T>> {

    @Nonnull
    final Thread thread;

    public ParkingLedgerSelector(@Nonnull Thread thread) {
        this.thread = thread;
    }

    @Override
    @Nonnull
    protected ParkingLedgerSelectionKey<T> newSelectionKey(@Nonnull WaitableHost host, T value) {
        return new ParkingLedgerSelectionKey<T>(this, host, value);
    }
}
