package codes.writeonce.utils;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import javax.annotation.Nonnull;

public interface ResponseFilter {

    @Nonnull
    ResponseFilter EMPTY = (request, response) -> {
        // empty
    };

    void filter(
            @Nonnull HttpRequest request,
            @Nonnull HttpResponse response
    );
}
