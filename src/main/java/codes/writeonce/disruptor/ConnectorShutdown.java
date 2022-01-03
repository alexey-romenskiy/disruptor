package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

class ConnectorShutdown {

    @Nonnull
    public final Connector connector;

    @Nonnull
    public final CompletableFuture<Void> future;

    public ConnectorShutdown(@Nonnull Connector connector, @Nonnull CompletableFuture<Void> future) {
        this.connector = connector;
        this.future = future;
    }
}
