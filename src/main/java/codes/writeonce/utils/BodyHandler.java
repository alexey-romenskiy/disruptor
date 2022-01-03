package codes.writeonce.utils;

import io.netty.handler.codec.http.HttpContent;

import javax.annotation.Nonnull;

public interface BodyHandler {

    void append(@Nonnull HttpContent httpContent) throws NettyRequestException;
}
