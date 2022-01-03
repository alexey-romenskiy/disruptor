package codes.writeonce.utils;

import codes.writeonce.disruptor.AbstractEvent;

public class WebsocketConnectedEvent extends AbstractEvent {

    private final long websocketId;

    public WebsocketConnectedEvent(long websocketId) {
        this.websocketId = websocketId;
    }

    public long getWebsocketId() {
        return websocketId;
    }
}
