package codes.writeonce.utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.util.AsciiString;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_MAX_AGE;
import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.DATE;
import static io.netty.handler.codec.http.HttpHeaderNames.ETAG;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
import static io.netty.handler.codec.http.HttpHeaderNames.LOCATION;
import static io.netty.handler.codec.http.HttpHeaderNames.ORIGIN;
import static io.netty.handler.codec.http.HttpHeaderNames.PRAGMA;
import static io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;
import static io.netty.handler.codec.http.HttpHeaderNames.VARY;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class ResponseUtils {

    public static final HashMap<AsciiString, String> CACHE_FOREVER_HEADERS = new HashMap<>();

    public static final HashMap<AsciiString, String> RECHECK_ALWAYS_HEADERS = new HashMap<>();

    static {
        CACHE_FOREVER_HEADERS.put(CACHE_CONTROL, "public, max-age=31536000");
        RECHECK_ALWAYS_HEADERS.put(CACHE_CONTROL, "public, max-age=0, no-cache, must-revalidate");
    }

    public static void sendError(
            @Nonnull ChannelHandlerContext context,
            @Nonnull HttpRequest request,
            @Nonnull HttpResponseStatus statusCode,
            @Nonnull String statusText,
            @Nullable String message,
            boolean forceClose
    ) {
        final var jsonObject = new JsonObject()
                .put("success", false)
                .put("status", statusText);

        if (message != null) {
            jsonObject.put("message", message);
        }

        send(context, request, statusCode, "application/json; charset=UTF-8", copiedBuffer(jsonObject.encode(), UTF_8),
                forceClose, null, null, null, Collections.emptyMap());
    }

    public static void send(
            @Nonnull ChannelHandlerContext context,
            @Nonnull HttpRequest request,
            @Nonnull HttpResponseStatus status,
            @Nullable String contentType,
            @Nonnull ByteBuf content,
            boolean forceClose,
            @Nullable Instant lastModified,
            @Nullable String eTag,
            @Nullable String contentEncoding,
            @Nonnull Map<AsciiString, String> customHeaders
    ) {
        final var response = new DefaultFullHttpResponse(HTTP_1_1, status, content);
        final var headers = response.headers();
        for (final var entry : customHeaders.entrySet()) {
            headers.set(entry.getKey(), entry.getValue());
        }
        if (contentType != null) {
            headers.set(CONTENT_TYPE, contentType);
        }
        if (lastModified != null) {
            headers.set(LAST_MODIFIED, DateFormatter.format(new Date(lastModified.toEpochMilli())));
        }
        if (eTag != null) {
            headers.set(ETAG, '"' + eTag + '"');
        }
        if (contentEncoding != null) {
            headers.set(CONTENT_ENCODING, contentEncoding);
        }
        HttpUtil.setContentLength(response, content.readableBytes());
        send(context, request, forceClose, response);
    }

    public static void sendOptions(
            @Nonnull ChannelHandlerContext context,
            @Nonnull HttpRequest request
    ) {
        final var response = new DefaultFullHttpResponse(HTTP_1_1, OK, EMPTY_BUFFER);
        final var headers = response.headers();
        final var origin = request.headers().get(ORIGIN);
        headers.set(ACCESS_CONTROL_ALLOW_ORIGIN, origin == null ? "*" : origin);
        headers.set(VARY, "Origin");
        headers.set(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        headers.set(ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PATCH, DELETE");
        headers.set(ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, X-Service-Api-Version");
        headers.set(ACCESS_CONTROL_MAX_AGE, String.valueOf(60 * 60 * 24));
        HttpUtil.setContentLength(response, 0);
        send(context, request, false, response);
    }

    public static void sendRedirect(
            @Nonnull ChannelHandlerContext context,
            @Nonnull HttpRequest request,
            boolean forceClose,
            @Nonnull HttpResponseStatus status,
            @Nonnull String uri,
            @Nonnull Cookie... cookies
    ) {
        final var response = new DefaultFullHttpResponse(HTTP_1_1, status, EMPTY_BUFFER);
        final var headers = response.headers();
        headers.set(LOCATION, uri);
        for (final var cookie : cookies) {
            headers.add(SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
        }
        headers.set(CACHE_CONTROL, "max-age=0, no-cache, no-store, must-revalidate");
        headers.set(PRAGMA, "no-cache");
        headers.set(EXPIRES, "Wed, 11 Jan 1984 05:00:00 GMT");
        send(context, request, forceClose, response);
    }

    private static void send(
            @Nonnull ChannelHandlerContext context,
            @Nonnull HttpRequest request,
            boolean forceClose,
            @Nonnull DefaultFullHttpResponse response
    ) {
        final var headers = response.headers();

        headers.set("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
        headers.set(DATE, DateFormatter.format(new Date()));

        final boolean keepAlive = !forceClose && HttpUtil.isKeepAlive(request);

        if (!keepAlive) {
            headers.set(CONNECTION, CLOSE);
        } else if (request.protocolVersion().equals(HTTP_1_0)) {
            headers.set(CONNECTION, KEEP_ALIVE);
        }

        final var future = context.writeAndFlush(response);

        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Nonnull
    public static String getUri(boolean secure, @Nonnull String domain, @Nonnull String path) {
        return (secure ? "https" : "http") + "://" + domain + path;
    }

    private ResponseUtils() {
        // empty
    }
}
