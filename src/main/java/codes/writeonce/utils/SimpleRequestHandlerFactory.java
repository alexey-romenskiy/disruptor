package codes.writeonce.utils;

import codes.writeonce.disruptor.WebSender;
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
            @Nonnull WebSender<T> sender,
            @Nonnull AtomicBoolean running,
            @Nonnull ResponseFilter responseFilter,
            @Nonnull EventFactory<T> eventFactory
    ) {
        super(sender, running, responseFilter);
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
