package codes.writeonce.utils;

import codes.writeonce.disruptor.AbstractEvent;

public class WebsocketMessageSentEvent extends AbstractEvent {

    private final long websocketId;

    public WebsocketMessageSentEvent(long websocketId) {
        this.websocketId = websocketId;
    }

    public long getWebsocketId() {
        return websocketId;
    }
}
