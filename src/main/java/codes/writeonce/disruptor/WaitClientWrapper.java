package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

public final class WaitClientWrapper {

    @Nonnull
    private final AtomicReference<WaitClient> head;

    @Nonnull
    private final WaitClient waitClient;

    public WaitClientWrapper(@Nonnull AtomicReference<WaitClient> head, @Nonnull WaitClient waitClient) {
        this.head = head;
        this.waitClient = waitClient;
    }

    public void await() {
        waitClient.await(head);
    }
}
