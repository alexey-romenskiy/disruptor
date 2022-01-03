package codes.writeonce.utils;

import codes.writeonce.disruptor.AbstractEvent;

public class WebsocketDisconnectedEvent extends AbstractEvent {

    private final long websocketId;

    public WebsocketDisconnectedEvent(long websocketId) {
        this.websocketId = websocketId;
    }

    public long getWebsocketId() {
        return websocketId;
    }
}
