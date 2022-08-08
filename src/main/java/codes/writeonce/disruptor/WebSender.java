package codes.writeonce.disruptor;

import codes.writeonce.utils.NettyRequestContext;
import codes.writeonce.utils.WebSocketProtocolHandler;

import javax.annotation.Nonnull;

public interface WebSender<T> {

    void send(long incomingNanos, @Nonnull T event, @Nonnull NettyRequestContext requestContext);

    void send(long incomingNanos, @Nonnull T event, @Nonnull WebSocketProtocolHandler webSocketProtocolHandler);
}
