package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.ArrayList;

public class DisruptorEntry<T> {

    public long incomingNanos;

    public Object[] slots;

    public boolean replay;

    public Instant timestamp;

    public T event;

    public ArrayList<ConnectorShutdown> connectorShutdowns;

    public DisruptorEntry(@Nonnull Slots slots) {
        this.slots = slots.init();
    }

    public void clean(@Nonnull Slots slots) {
        incomingNanos = 0;
        replay = false;
        timestamp = null;
        event = null;
        connectorShutdowns = null;
        slots.clean(this.slots);
    }

    public synchronized void addConnectorShutdown(@Nonnull Connector connector) throws Exception {

        if (connectorShutdowns == null) {
            connectorShutdowns = new ArrayList<>();
        }

        connectorShutdowns.add(new ConnectorShutdown(connector, connector.shutdown()));
    }
}
