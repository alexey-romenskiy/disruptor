package codes.writeonce.utils;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleMapping implements Mapping {

    private final Map<String, Resource> uriToMethodToHandlerMap = new HashMap<>();

    @Nonnull
    public Mapping get(@Nonnull String uri, @Nonnull RequestHandlerFactory factory) {
        uriToMethodToHandlerMap.computeIfAbsent(uri, k -> new Resource()).setGet(factory);
        return this;
    }

    @Nonnull
    public Mapping post(@Nonnull String uri, @Nonnull RequestHandlerFactory factory) {
        uriToMethodToHandlerMap.computeIfAbsent(uri, k -> new Resource()).setPost(factory);
        return this;
    }

    @Nonnull
    public Mapping patch(@Nonnull String uri, @Nonnull RequestHandlerFactory factory) {
        uriToMethodToHandlerMap.computeIfAbsent(uri, k -> new Resource()).setPatch(factory);
        return this;
    }

    @Nonnull
    public Mapping delete(@Nonnull String uri, @Nonnull RequestHandlerFactory factory) {
        uriToMethodToHandlerMap.computeIfAbsent(uri, k -> new Resource()).setDelete(factory);
        return this;
    }

    @Nullable
    @Override
    public Resource handle(
            @Nonnull HttpRequest request,
            @Nonnull QueryStringDecoder queryStringDecoder,
            @Nonnull Map<String, List<Cookie>> cookies
    ) {
        return uriToMethodToHandlerMap.get(queryStringDecoder.path());
    }

    @Nonnull
    @Override
    public ResponseFilter getResponseFilter() {
        return ResponseFilter.EMPTY;
    }
}
