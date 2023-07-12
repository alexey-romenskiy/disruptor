package codes.writeonce.concurrency;

import java.util.concurrent.atomic.AtomicReference;

public class Bloom {

    final AtomicReference<Bee> head = new AtomicReference<>();

    public void ready() {

        var bee = head.getAndSet(null);
        while (bee != null) {
            final var next = bee.nextWaiting;
            bee.nextWaiting = null;
            bee.busy.set(false);
            bee.accept();
            bee = next;
        }
    }
}
