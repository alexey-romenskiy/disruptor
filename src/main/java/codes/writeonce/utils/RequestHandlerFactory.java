package codes.writeonce.utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public interface RequestHandlerFactory {

    @Nonnull
    RequestHandler handle(
            @Nonnull ChannelHandlerContext context,
            @Nonnull HttpRequest request,
            @Nonnull QueryStringDecoder queryStringDecoder,
            @Nonnull Map<String, List<Cookie>> cookies
    ) throws NettyRequestException;
}
