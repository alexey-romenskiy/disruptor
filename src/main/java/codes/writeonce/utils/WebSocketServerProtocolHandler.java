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

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.util.AttributeKey;

import javax.annotation.Nonnull;
import java.util.List;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * This handler does all the heavy lifting for you to run a websocket server.
 * <p>
 * It takes care of websocket handshaking as well as processing of control frames (Close, Ping, Pong). Text and Binary
 * data frames are passed to the next handler in the pipeline (implemented by you) for processing.
 * <p>
 * See <code>io.netty.example.http.websocketx.html5.WebSocketServer</code> for usage.
 * <p>
 * The implementation of this handler assumes that you just want to run  a websocket server and not process other types
 * HTTP requests (like GET and POST). If you wish to support both HTTP requests and websockets in the one server, refer
 * to the <code>io.netty.example.http.websocketx.server.WebSocketServer</code> example.
 * <p>
 * To know once a handshake was done you can intercept the
 * {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)} and check if the event was instance
 * of {@link HandshakeComplete}, the event will contain extra information about the handshake such as the request and
 * selected subprotocol.
 */
public class WebSocketServerProtocolHandler extends WebSocketProtocolHandler {

    /**
     * Events that are fired to notify about handshake status
     */
    public enum ServerHandshakeStateEvent {
        /**
         * The Handshake was completed successfully and the channel was upgraded to websockets.
         *
         * @deprecated in favor of {@link HandshakeComplete} class,
         * it provides extra information about the handshake
         */
        @Deprecated
        HANDSHAKE_COMPLETE,

        /**
         * The Handshake was timed out
         */
        HANDSHAKE_TIMEOUT
    }

    /**
     * The Handshake was completed successfully and the channel was upgraded to websockets.
     */
    public static final class HandshakeComplete {

        private final String requestUri;
        private final HttpHeaders requestHeaders;
        private final String selectedSubprotocol;

        HandshakeComplete(String requestUri, HttpHeaders requestHeaders, String selectedSubprotocol) {
            this.requestUri = requestUri;
            this.requestHeaders = requestHeaders;
            this.selectedSubprotocol = selectedSubprotocol;
        }

        public String requestUri() {
            return requestUri;
        }

        public HttpHeaders requestHeaders() {
            return requestHeaders;
        }

        public String selectedSubprotocol() {
            return selectedSubprotocol;
        }
    }

    private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER_ATTR_KEY =
            AttributeKey.valueOf(WebSocketServerHandshaker.class, "HANDSHAKER");

    private final WebSocketServerProtocolConfig serverConfig;

    /**
     * Base constructor
     *
     * @param serverConfig Server protocol configuration.
     */
    WebSocketServerProtocolHandler(WebSocketServerProtocolConfig serverConfig,
            @Nonnull WebsocketMessageFactory websocketMessageFactory) {
        super(checkNotNull(serverConfig, "serverConfig").dropPongFrames(),
                serverConfig.sendCloseFrame(),
                serverConfig.forceCloseTimeoutMillis(),
                websocketMessageFactory);
        this.serverConfig = serverConfig;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
        if (serverConfig.handleCloseFrames() && frame instanceof CloseWebSocketFrame) {
            WebSocketServerHandshaker handshaker = getHandshaker(ctx.channel());
            if (handshaker != null) {
                frame.retain();
                ChannelPromise promise = ctx.newPromise();
                closeSent(promise);
                handshaker.close(ctx, (CloseWebSocketFrame) frame, promise);
            } else {
                ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
            return;
        }
        super.decode(ctx, frame, out);
    }

    @Override
    protected WebSocketServerHandshakeException buildHandshakeException(String message) {
        return new WebSocketServerHandshakeException(message);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof WebSocketHandshakeException) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HTTP_1_1, HttpResponseStatus.BAD_REQUEST, Unpooled.wrappedBuffer(cause.getMessage().getBytes()));
            ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireExceptionCaught(cause);
            ctx.close();
        }
    }

    static WebSocketServerHandshaker getHandshaker(Channel channel) {
        return channel.attr(HANDSHAKER_ATTR_KEY).get();
    }

    static void setHandshaker(Channel channel, WebSocketServerHandshaker handshaker) {
        channel.attr(HANDSHAKER_ATTR_KEY).set(handshaker);
    }
}
