package codes.writeonce.utils;

import codes.writeonce.disruptor.Sender;
import codes.writeonce.disruptor.Slot;
import codes.writeonce.disruptor.Slots;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractSwitchableRequestHandlerFactory<T> implements RequestHandlerFactory {

    @Nonnull
    final Sender<T> sender;

    @Nonnull
    final Slot<NettyRequestContext> requestSlot;

    @Nonnull
    final AtomicBoolean running;

    public AbstractSwitchableRequestHandlerFactory(
            @Nonnull Sender<T> sender,
            @Nonnull Slots slots,
            @Nonnull AtomicBoolean running
    ) {
        this.sender = sender;
        this.requestSlot = slots.slot(NettyRequestSlotKey.INSTANCE);
        this.running = running;
    }
}
