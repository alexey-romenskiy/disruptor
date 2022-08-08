package codes.writeonce.utils;

import codes.writeonce.disruptor.WebSender;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractSwitchableRequestHandlerFactory<T> implements RequestHandlerFactory {

    @Nonnull
    final WebSender<T> sender;

    @Nonnull
    final AtomicBoolean running;

    public AbstractSwitchableRequestHandlerFactory(
            @Nonnull WebSender<T> sender,
            @Nonnull AtomicBoolean running
    ) {
        this.sender = sender;
        this.running = running;
    }
}
