package codes.writeonce.utils;

import codes.writeonce.disruptor.SlotKey;

import javax.annotation.Nonnull;

public class NettyRequestSlotKey implements SlotKey<NettyRequestContext> {

    public static final NettyRequestSlotKey INSTANCE = new NettyRequestSlotKey();

    @Override
    public NettyRequestContext init() {
        return null;
    }

    @Override
    public NettyRequestContext clean(@Nonnull NettyRequestContext value) {
        return null;
    }
}
