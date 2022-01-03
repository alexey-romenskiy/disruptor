package codes.writeonce.utils;

import codes.writeonce.disruptor.Sender;
import codes.writeonce.disruptor.Slots;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleRequestHandlerFactory<T> extends AbstractSwitchableRequestHandlerFactory<T> {

    @Nonnull
    private final EventFactory<T> eventFactory;

    public SimpleRequestHandlerFactory(
            @Nonnull Sender<T> sender,
            @Nonnull Slots slots,
            @Nonnull AtomicBoolean running,
            @Nonnull EventFactory<T> eventFactory
    ) {
        super(sender, slots, running);
        this.eventFactory = eventFactory;
    }

    @Nonnull
    @Override
    public RequestHandler handle(
            @Nonnull ChannelHandlerContext context,
            @Nonnull HttpRequest request,
            @Nonnull QueryStringDecoder queryStringDecoder,
            @Nonnull Map<String, List<Cookie>> cookies
    ) {
        return new AbstractSwitchableRequestHandler<>(this) {

            @Nullable
            @Override
            public BodyHandler getBodyHandler() {
                return null;
            }

            @Override
            public void end(@Nonnull NettyRequestContext requestContext) throws NettyRequestException {
                final var event = eventFactory.newEvent(requestContext);
                send(event, requestContext);
            }

            @Override
            public void cleanup() {
                // empty
            }
        };
    }

    public interface EventFactory<T> {

        @Nonnull
        T newEvent(@Nonnull NettyRequestContext requestContext) throws NettyRequestException;
    }
}
