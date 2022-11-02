package codes.writeonce.utils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResultProvider;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CustomHttpServerHandler extends SimpleChannelInboundHandler<Object> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(CustomHttpServerHandler.class);

    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

    @Nonnull
    private final Mapping mapping;

    private ChannelHandlerContext context;

    private long requestId;

    private HttpRequest request;

    private QueryStringDecoder queryStringDecoder;

    private Map<String, List<Cookie>> cookies;

    private RequestHandler requestHandler;

    private BodyHandler bodyHandler;

    public CustomHttpServerHandler(@Nonnull Mapping mapping) {
        this.mapping = mapping;
    }

    @Override
    public void handlerAdded(@Nonnull ChannelHandlerContext context) {
        this.context = context;
    }

    @Override
    protected void channelRead0(@Nonnull ChannelHandlerContext ctx, @Nonnull Object msg) {

        try {
            MDC.put("rs", String.valueOf(requestId));
            doChannelRead0(ctx, msg);
        } finally {
            MDC.remove("rs");
        }
    }

    private void doChannelRead0(@Nonnull ChannelHandlerContext ctx, @Nonnull Object msg) {

        if (msg instanceof final DecoderResultProvider drp) {
            final var result = drp.decoderResult();
            if (!result.isSuccess()) {
                LOGGER.error("Decoder Failure", result.cause());
                sendError(BAD_REQUEST, "MALFORMED_REQUEST", "Request decoder Failure");
                return;
            }
        }

        if (msg instanceof final HttpRequest r) {

            requestId = REQUEST_SEQUENCE.incrementAndGet();
            MDC.put("rs", String.valueOf(requestId));

            request = r;

            LOGGER.info(
                    "Received incoming request {} {} version={} remoteAddress={} headers={}",
                    r.method().name(),
                    r.uri(),
                    r.protocolVersion().text(),
                    context.channel().remoteAddress(),
                    r.headers()
            );

            queryStringDecoder = new QueryStringDecoder(r.uri(), UTF_8);

            final var cookieValue = request.headers().get(HttpHeaderNames.COOKIE);
            cookies = cookieValue == null
                    ? Collections.emptyMap()
                    : group(ServerCookieDecoder.STRICT.decodeAll(cookieValue));

            final Mapping.Resource methods;

            try {
                methods = mapping.handle(r, queryStringDecoder, cookies);
            } catch (NettyRequestException e) {
                LOGGER.error("Failed to process request", e);
                sendError(e.getHttpResponseStatus(), "MALFORMED_REQUEST", e.getMessage());
                return;
            } catch (Exception e) {
                LOGGER.error("Failed to process request", e);
                sendError(BAD_REQUEST, "MALFORMED_REQUEST", "Invalid request parameters");
                return;
            }

            if (methods == null) {
                sendError(NOT_FOUND, "NOT_FOUND", "No resource at this URI");
                return;
            }

            final var factory = switch (r.method().name()) {
                case "GET" -> methods.get;
                case "POST" -> methods.post;
                case "PATCH" -> methods.patch;
                case "DELETE" -> methods.delete;
                case "OPTIONS" -> methods.options;
                default -> null;
            };

            if (factory == null) {
                sendError(METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Request method not supported for this URI");
                return;
            }

            try {
                requestHandler = factory.handle(context, r, queryStringDecoder, cookies);
                bodyHandler = requestHandler.getBodyHandler();
            } catch (NettyRequestException e) {
                LOGGER.error("Failed to process request", e);
                sendError(e.getHttpResponseStatus(), "MALFORMED_REQUEST", e.getMessage());
                return;
            } catch (Exception e) {
                LOGGER.error("Failed to process request", e);
                sendError(BAD_REQUEST, "MALFORMED_REQUEST", "Invalid request parameters");
                return;
            }

            if (HttpUtil.is100ContinueExpected(r)) {
                ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE, EMPTY_BUFFER));
            }
        }

        if (request != null && msg instanceof final HttpContent httpContent) {
            try {
                if (bodyHandler != null) {
                    bodyHandler.append(httpContent);
                }
                if (msg instanceof final LastHttpContent trailer) {

                    LOGGER.info(
                            "Full incoming request {} {} version={} remoteAddress={} headers={} trailers={}",
                            request.method().name(),
                            request.uri(),
                            request.protocolVersion().text(),
                            context.channel().remoteAddress(),
                            request.headers(),
                            trailer.trailingHeaders()
                    );

                    if (requestHandler != null) {
                        requestHandler.end(
                                new NettyRequestContext(context, request, queryStringDecoder, cookies, trailer));
                    }

                    cleanup();
                }
            } catch (NettyRequestException e) {
                LOGGER.error("Failed to process request", e);
                sendError(e.getHttpResponseStatus(), "MALFORMED_REQUEST", e.getMessage());
            } catch (Exception e) {
                LOGGER.error("Failed to process request", e);
                sendError(BAD_REQUEST, "MALFORMED_REQUEST", "Invalid request data");
            }
        }
    }

    @Nonnull
    private Map<String, List<Cookie>> group(@Nonnull List<Cookie> cookies) {
        final var map = new LinkedHashMap<String, List<Cookie>>(cookies.size());
        for (final var cookie : cookies) {
            map.computeIfAbsent(cookie.name(), k -> new ArrayList<>(1)).add(cookie);
        }
        return map;
    }

    private void cleanup() {

        requestId = 0;
        request = null;
        queryStringDecoder = null;
        cookies = null;
        bodyHandler = null;
        if (requestHandler != null) {
            requestHandler.cleanup();
            requestHandler = null;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        cleanup();
    }

    private void sendError(
            @Nonnull HttpResponseStatus status,
            @Nonnull String statusText,
            @Nonnull String message
    ) {
        ResponseUtils.sendError(context, request, status, statusText, message, true);
        cleanup();
    }
}
