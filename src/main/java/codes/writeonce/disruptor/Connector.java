package codes.writeonce.disruptor;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public interface Connector {

    @Nonnull
    CompletableFuture<Void> shutdown() throws Exception;
}
