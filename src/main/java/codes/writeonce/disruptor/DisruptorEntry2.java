package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.ArrayList;

public class DisruptorEntry2<T> {

    public long incomingNanos;

    public boolean replay;

    public Instant timestamp;

    public T event;

    public ArrayList<ConnectorShutdown> connectorShutdowns;

    public void clean() {
        incomingNanos = 0;
        replay = false;
        timestamp = null;
        event = null;
        connectorShutdowns = null;
    }

    public synchronized void addConnectorShutdown(@Nonnull Connector connector) throws Exception {

        if (connectorShutdowns == null) {
            connectorShutdowns = new ArrayList<>();
        }

        connectorShutdowns.add(new ConnectorShutdown(connector, connector.shutdown()));
    }
}
