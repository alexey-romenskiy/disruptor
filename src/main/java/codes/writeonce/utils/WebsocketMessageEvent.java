package codes.writeonce.utils;

import codes.writeonce.disruptor.AbstractEvent;

import javax.annotation.Nonnull;

public class WebsocketMessageEvent extends AbstractEvent {

    private final long websocketId;

    @Nonnull
    private final String text;

    public WebsocketMessageEvent(long websocketId, @Nonnull String text) {
        this.websocketId = websocketId;
        this.text = text;
    }

    public long getWebsocketId() {
        return websocketId;
    }

    @Nonnull
    public String getText() {
        return text;
    }
}
