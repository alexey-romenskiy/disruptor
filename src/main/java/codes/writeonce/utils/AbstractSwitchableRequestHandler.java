package codes.writeonce.utils;

import codes.writeonce.disruptor.Sender;
import codes.writeonce.disruptor.Slot;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;

public abstract class AbstractSwitchableRequestHandler<T> implements RequestHandler {

    @Nonnull
    private final Sender<T> sender;

    @Nonnull
    private final Slot<NettyRequestContext> requestSlot;

    @Nonnull
    private final AtomicBoolean running;

    public AbstractSwitchableRequestHandler(@Nonnull AbstractSwitchableRequestHandlerFactory<T> factory) {
        sender = factory.sender;
        requestSlot = factory.requestSlot;
        ;
        running = factory.running;
    }

    protected void send(@Nonnull T event, @Nonnull NettyRequestContext requestContext) {

        if (running.get()) {
            sender.send(System.nanoTime(), event, requestSlot, requestContext);
        } else {
            ResponseUtils.sendError(requestContext.getContext(), requestContext.getRequest(), SERVICE_UNAVAILABLE,
                    "SERVICE_UNAVAILABLE", null, false);
        }
    }
}
