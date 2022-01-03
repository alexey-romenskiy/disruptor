package codes.writeonce.utils;

import io.netty.handler.codec.http.HttpResponseStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serial;

import static java.util.Objects.requireNonNull;

public class NettyRequestException extends Exception {

    @Serial
    private static final long serialVersionUID = 2509266111968348380L;

    @Nonnull
    private final HttpResponseStatus httpResponseStatus;

    public NettyRequestException(@Nonnull HttpResponseStatus httpResponseStatus, @Nonnull String message) {
        super(requireNonNull(message));
        this.httpResponseStatus = requireNonNull(httpResponseStatus);
    }

    public NettyRequestException(@Nonnull HttpResponseStatus httpResponseStatus, @Nonnull String message,
            @Nullable Throwable cause) {
        super(requireNonNull(message), cause);
        this.httpResponseStatus = requireNonNull(httpResponseStatus);
    }

    public NettyRequestException(@Nonnull HttpResponseStatus httpResponseStatus, @Nonnull String message,
            @Nullable Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.httpResponseStatus = httpResponseStatus;
    }

    @Nonnull
    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }
}
