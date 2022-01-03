package codes.writeonce.utils;

import codes.writeonce.disruptor.SlotKey;

import javax.annotation.Nonnull;

public class NettyWebSocketSlotKey implements SlotKey<WebSocketProtocolHandler> {

    public static final NettyWebSocketSlotKey INSTANCE = new NettyWebSocketSlotKey();

    @Override
    public WebSocketProtocolHandler init() {
        return null;
    }

    @Override
    public WebSocketProtocolHandler clean(@Nonnull WebSocketProtocolHandler value) {
        return null;
    }
}
