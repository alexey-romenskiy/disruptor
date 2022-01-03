package codes.writeonce.disruptor;

import javax.annotation.Nonnull;

public interface EventSender<T> {

    void send(long incomingNanos, @Nonnull T event);
}
