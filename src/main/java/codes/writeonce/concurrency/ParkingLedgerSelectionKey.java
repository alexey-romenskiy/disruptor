package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.util.concurrent.locks.LockSupport;

public class ParkingLedgerSelectionKey<T> extends LedgerSelectionKey<T, ParkingLedgerSelector<T>> {

    public ParkingLedgerSelectionKey(
            @Nonnull ParkingLedgerSelector<T> ledgerSelector,
            @Nonnull WaitableHost host,
            T value
    ) {
        super(ledgerSelector, host, value);
    }

    @Override
    void accept() {
        super.accept();
        LockSupport.unpark(selector.thread);
    }
}
