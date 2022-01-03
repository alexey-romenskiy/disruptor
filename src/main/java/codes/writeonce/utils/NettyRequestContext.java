package codes.writeonce.utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public class NettyRequestContext {

    @Nonnull
    private final ChannelHandlerContext context;

    @Nonnull
    private final HttpRequest request;

    @Nonnull
    private final QueryStringDecoder queryStringDecoder;

    @Nonnull
    private final Map<String, List<Cookie>> cookies;

    @Nonnull
    private final LastHttpContent trailer;

    public NettyRequestContext(
            @Nonnull ChannelHandlerContext context,
            @Nonnull HttpRequest request,
            @Nonnull QueryStringDecoder queryStringDecoder,
            @Nonnull Map<String, List<Cookie>> cookies,
            @Nonnull LastHttpContent trailer
    ) {
        this.context = context;
        this.request = request;
        this.queryStringDecoder = queryStringDecoder;
        this.cookies = cookies;
        this.trailer = trailer;
    }

    @Nonnull
    public ChannelHandlerContext getContext() {
        return context;
    }

    @Nonnull
    public HttpRequest getRequest() {
        return request;
    }

    @Nonnull
    public QueryStringDecoder getQueryStringDecoder() {
        return queryStringDecoder;
    }

    @Nonnull
    public Map<String, List<Cookie>> getCookies() {
        return cookies;
    }

    @Nonnull
    public LastHttpContent getTrailer() {
        return trailer;
    }
}
