package codes.writeonce.utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Nonnull
    private final WebSocketServerProtocolHandler webSocketServerProtocolHandler;

    @Nonnull
    private final WebsocketMessageFactory websocketMessageFactory;

    private ChannelHandlerContext context;

    public WebSocketHandler(@Nonnull WebSocketServerProtocolHandler webSocketServerProtocolHandler,
            @Nonnull WebsocketMessageFactory websocketMessageFactory) {
        this.webSocketServerProtocolHandler = webSocketServerProtocolHandler;
        this.websocketMessageFactory = websocketMessageFactory;
    }

    @Override
    public void handlerAdded(@Nonnull ChannelHandlerContext context) {
        this.context = context;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof WebSocketFrame) {
            if (msg instanceof final BinaryWebSocketFrame m) {
                logger.info("BinaryWebSocketFrame: {}", m.content());
            } else if (msg instanceof final TextWebSocketFrame m) {
                logger.info("TextWebSocketFrame: {}", m.text());
                websocketMessageFactory.websocketMessageEvent(webSocketServerProtocolHandler,
                        webSocketServerProtocolHandler.websocketId, m.text());
            } else if (msg instanceof final PingWebSocketFrame m) {
                logger.info("PingWebSocketFrame: {}", m.content());
            } else if (msg instanceof final PongWebSocketFrame m) {
                logger.info("PongWebSocketFrame: {}", m.content());
            } else if (msg instanceof final CloseWebSocketFrame m) {
                logger.info("CloseWebSocketFrame: {} {}", m.statusCode(), m.reasonText());
            } else {
                logger.info("Unsupported WebSocketFrame: {}", msg);
            }
        }
    }
}
