package codes.writeonce.disruptor;

import javax.annotation.Nonnull;

public class EventHolder<T> {

    public long incomingNanos;

    public Object[] slots;

    public T event;

    public EventHolder(@Nonnull Slots slots) {
        this.slots = slots.init();
    }

    public void clean() {
        incomingNanos = 0;
        event = null;
    }
}
