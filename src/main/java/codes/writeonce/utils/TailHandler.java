package codes.writeonce.utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.net.SocketException;

public class TailHandler extends ChannelInboundHandlerAdapter {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) throws Exception {
        try {
            if (throwable instanceof final SocketException e) {
                final var message = e.getMessage();
                if ("Connection reset".equals(message)) {
                    logger.info("Unhandled exception: {}", message);
                    return;
                }
            } else if (throwable instanceof final DecoderException e) {
                final var cause = e.getCause();
                if (cause instanceof SSLHandshakeException) {
                    logger.info("Unhandled exception: {}", e.getMessage());
                    return;
                }
            }
            logger.error("Unhandled exception", throwable);
        } finally {
            ReferenceCountUtil.release(throwable);
            ctx.close();
        }
    }
}
