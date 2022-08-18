package codes.writeonce.disruptor;

public class EventHolder2<T> {

    public long incomingNanos;

    public T event;

    public void clean() {
        incomingNanos = 0;
        event = null;
    }
}
