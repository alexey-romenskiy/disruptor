/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package codes.writeonce.utils;

import codes.writeonce.utils.WebSocketServerProtocolHandler.ServerHandshakeStateEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.Utf8FrameValidator;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Handles the HTTP handshake (the HTTP Upgrade request) for {@link WebSocketServerProtocolHandler}.
 */
class WebSocketServerProtocolHandshakeHandler extends ChannelInboundHandlerAdapter {

    private static final AtomicLong ID_SEQ = new AtomicLong();

    private final WebSocketServerProtocolConfig serverConfig;

    private final WebSocketServerProtocolHandler webSocketServerProtocolHandler;

    @Nonnull
    private final WebsocketMessageFactory websocketMessageFactory;

    private ChannelHandlerContext ctx;

    private ChannelPromise handshakePromise;

    WebSocketServerProtocolHandshakeHandler(@Nonnull WebsocketMessageFactory websocketMessageFactory) {
        this.serverConfig = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/")
                .subprotocols(null)
                .checkStartsWith(false)
                .handshakeTimeoutMillis(10000)
                .dropPongFrames(true)
                .forceCloseTimeoutMillis(5000)
                .decoderConfig(WebSocketDecoderConfig.newBuilder()
                        .maxFramePayloadLength(65536)
                        .allowMaskMismatch(false)
                        .allowExtensions(true)
                        .build())
                .build();
        this.webSocketServerProtocolHandler = new WebSocketServerProtocolHandler(serverConfig, websocketMessageFactory);
        this.websocketMessageFactory = websocketMessageFactory;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        handshakePromise = ctx.newPromise();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

        if (!(msg instanceof final HttpRequest req)) {
            ctx.fireChannelRead(msg);
            return;
        }

        final var webSocketPath = getWebSocketPath(req);
        if (webSocketPath == null) {
            ctx.fireChannelRead(msg);
            return;
        }

        final var headers = req.headers();

        if (!"Upgrade".equalsIgnoreCase(headers.get(CONNECTION)) ||
            !"WebSocket".equalsIgnoreCase(headers.get(UPGRADE))) {
            ctx.fireChannelRead(msg);
            return;
        }

        final var p = ctx.pipeline();

        p.remove(ChunkedWriteHandler.class);
        p.remove(CustomHttpServerHandler.class);
        final var tailContext = p.context(TailHandler.class);
        final var tailName = tailContext.name();
        p.addBefore(tailName, null, new WebSocketServerCompressionHandler());
        if (serverConfig.decoderConfig().withUTF8Validator() && p.get(Utf8FrameValidator.class) == null) {
            // Add the UFT8 checking before this one.
            p.addBefore(tailName, Utf8FrameValidator.class.getName(), new Utf8FrameValidator());
        }
        p.addBefore(tailName, null, webSocketServerProtocolHandler);
        p.addBefore(tailName, null, new WebSocketHandler(webSocketServerProtocolHandler, websocketMessageFactory));

        webSocketServerProtocolHandler.websocketId = ID_SEQ.incrementAndGet();

        try {
            if (!GET.equals(req.method())) {
                sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN, ctx.alloc().buffer(0)));
                return;
            }

            final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                    getWebSocketLocation(p, req, webSocketPath),
                    serverConfig.subprotocols(), serverConfig.decoderConfig());
            final WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);
            final ChannelPromise localHandshakePromise = handshakePromise;
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel())
                        .addListener(ChannelFutureListener.CLOSE);
            } else {
                // Ensure we set the handshaker and replace this handler before we
                // trigger the actual handshake. Otherwise we may receive websocket bytes in this handler
                // before we had a chance to replace it.
                //
                // See https://github.com/netty/netty/issues/9471.
                WebSocketServerProtocolHandler.setHandshaker(ctx.channel(), handshaker);
                p.remove(this);

                final ChannelFuture handshakeFuture = handshaker.handshake(ctx.channel(), req);
                handshakeFuture.addListener(new ChannelFutureListener() {
                    @SuppressWarnings("deprecation")
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        if (!future.isSuccess()) {
                            localHandshakePromise.tryFailure(future.cause());
                            ctx.fireExceptionCaught(future.cause());
                        } else {
                            localHandshakePromise.trySuccess();
                            // Kept for compatibility
                            ctx.fireUserEventTriggered(
                                    ServerHandshakeStateEvent.HANDSHAKE_COMPLETE);
                            ctx.fireUserEventTriggered(
                                    new WebSocketServerProtocolHandler.HandshakeComplete(
                                            req.uri(), headers, handshaker.selectedSubprotocol()));

                            webSocketServerProtocolHandler.closeLock.lock();
                            try {
                                if (webSocketServerProtocolHandler.closedFlag == 0) {
                                    webSocketServerProtocolHandler.closedFlag = 1;
                                    websocketMessageFactory.websocketConnectedEvent(webSocketServerProtocolHandler,
                                            webSocketServerProtocolHandler.websocketId);
                                }
                            } finally {
                                webSocketServerProtocolHandler.closeLock.unlock();
                            }
                        }
                    }
                });
                applyHandshakeTimeout();
            }
        } finally {
            if (req instanceof final ReferenceCounted rc) {
                rc.release();
            }
        }
    }

    @Nullable
    private String getWebSocketPath(HttpRequest req) {
        return req.uri();
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static String getWebSocketLocation(ChannelPipeline cp, HttpRequest req, String webSocketPath) {
        return (cp.get(SslHandler.class) != null ? "wss" : "ws") + "://" + req.headers().get(HttpHeaderNames.HOST) +
               webSocketPath;
    }

    private void applyHandshakeTimeout() {
        final ChannelPromise localHandshakePromise = handshakePromise;
        final long handshakeTimeoutMillis = serverConfig.handshakeTimeoutMillis();
        if (handshakeTimeoutMillis <= 0 || localHandshakePromise.isDone()) {
            return;
        }

        final Future<?> timeoutFuture = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (!localHandshakePromise.isDone() &&
                    localHandshakePromise.tryFailure(new WebSocketServerHandshakeException("handshake timed out"))) {
                    ctx.flush()
                            .fireUserEventTriggered(ServerHandshakeStateEvent.HANDSHAKE_TIMEOUT)
                            .close();
                }
            }
        }, handshakeTimeoutMillis, TimeUnit.MILLISECONDS);

        // Cancel the handshake timeout when handshake is finished.
        localHandshakePromise.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> f) {
                timeoutFuture.cancel(false);
            }
        });
    }
}
