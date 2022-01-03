package codes.writeonce.utils;

import codes.writeonce.disruptor.AbstractEvent;

public class WebsocketMessageNotSentEvent extends AbstractEvent {

    private final long websocketId;

    public WebsocketMessageNotSentEvent(long websocketId) {
        this.websocketId = websocketId;
    }

    public long getWebsocketId() {
        return websocketId;
    }
}
