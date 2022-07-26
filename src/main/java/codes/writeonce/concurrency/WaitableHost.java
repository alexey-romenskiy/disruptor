package codes.writeonce.concurrency;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

public class WaitableHost {

    private final AtomicReference<Waitable> head = new AtomicReference<>();

    void register(@Nonnull Waitable item) {

        if (!item.ready.getAndSet(true)) {
            var next = head.get();
            while (true) {
                item.nextWaiting = next;
                final var w = head.compareAndExchange(next, item);
                if (w == next) {
                    break;
                }
                next = w;
            }
        }
    }

    public void wakeup() {

        var item = head.getAndSet(null);
        while (item != null) {
            final var next = item.nextWaiting;
            item.nextWaiting = null;
            item.ready.set(false);
            item.accept();
            item = next;
        }
    }
}
