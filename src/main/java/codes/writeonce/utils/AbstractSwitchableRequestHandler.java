package codes.writeonce.utils;

import codes.writeonce.disruptor.WebSender;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;

public abstract class AbstractSwitchableRequestHandler<T> implements RequestHandler {

    @Nonnull
    private final WebSender<T> sender;

    @Nonnull
    private final AtomicBoolean running;

    @Nonnull
    protected final ResponseFilter responseFilter;

    public AbstractSwitchableRequestHandler(@Nonnull AbstractSwitchableRequestHandlerFactory<T> factory) {
        sender = factory.sender;
        running = factory.running;
        responseFilter = factory.responseFilter;
    }

    protected void send(@Nonnull T event, @Nonnull NettyRequestContext requestContext) {

        if (running.get()) {
            sender.send(System.nanoTime(), event, requestContext);
        } else {
            ResponseUtils.sendError(requestContext.getContext(), requestContext.getRequest(), SERVICE_UNAVAILABLE,
                    "SERVICE_UNAVAILABLE", null, false, responseFilter);
        }
    }
}
