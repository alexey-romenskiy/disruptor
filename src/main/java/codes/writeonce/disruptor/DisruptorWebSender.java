package codes.writeonce.disruptor;

import codes.writeonce.utils.NettyRequestContext;
import codes.writeonce.utils.WebSocketProtocolHandler;

import javax.annotation.Nonnull;

public class DisruptorWebSender<T> implements WebSender<T> {

    @Nonnull
    private final Sender<T> sender;

    @Nonnull
    private final Slot<NettyRequestContext> requestSlot;

    @Nonnull
    private final Slot<WebSocketProtocolHandler> websocketSlot;

    public DisruptorWebSender(
            @Nonnull Sender<T> sender,
            @Nonnull Slot<NettyRequestContext> requestSlot,
            @Nonnull Slot<WebSocketProtocolHandler> websocketSlot
    ) {
        this.sender = sender;
        this.requestSlot = requestSlot;
        this.websocketSlot = websocketSlot;
    }

    @Override
    public void send(long incomingNanos, @Nonnull T event, @Nonnull NettyRequestContext requestContext) {
        sender.send(incomingNanos, event, requestSlot, requestContext);
    }

    @Override
    public void send(long incomingNanos, @Nonnull T event, @Nonnull WebSocketProtocolHandler webSocketProtocolHandler) {
        sender.send(incomingNanos, event, websocketSlot, webSocketProtocolHandler);
    }
}
