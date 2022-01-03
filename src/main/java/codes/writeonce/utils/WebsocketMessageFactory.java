package codes.writeonce.utils;

import javax.annotation.Nonnull;

public interface WebsocketMessageFactory {

    void websocketMessageEvent(@Nonnull WebSocketProtocolHandler webSocketProtocolHandler, long websocketId,
            @Nonnull String text);

    void websocketDisconnectedEvent(@Nonnull WebSocketProtocolHandler webSocketProtocolHandler, long websocketId);

    void websocketConnectedEvent(@Nonnull WebSocketProtocolHandler webSocketProtocolHandler, long websocketId);
}
