/*
 * Copyright 2013 The Netty Project
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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.PromiseNotifier;

import javax.annotation.Nonnull;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class WebSocketProtocolHandler extends MessageToMessageDecoder<WebSocketFrame>
        implements ChannelOutboundHandler {

    private final boolean dropPongFrames;
    private final WebSocketCloseStatus closeStatus;
    private final long forceCloseTimeoutMillis;
    private ChannelPromise closeSent;
    ChannelHandlerContext context;
    long websocketId;

    @Nonnull
    private final WebsocketMessageFactory websocketMessageFactory;

    int closedFlag;
    final Lock closeLock = new ReentrantLock();

    WebSocketProtocolHandler(boolean dropPongFrames,
            WebSocketCloseStatus closeStatus,
            long forceCloseTimeoutMillis, @Nonnull WebsocketMessageFactory websocketMessageFactory) {
        this.dropPongFrames = dropPongFrames;
        this.closeStatus = closeStatus;
        this.forceCloseTimeoutMillis = forceCloseTimeoutMillis;
        this.websocketMessageFactory = websocketMessageFactory;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        context = ctx;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
        if (frame instanceof PingWebSocketFrame) {
            frame.content().retain();
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content()));
            readIfNeeded(ctx);
            return;
        }
        if (frame instanceof PongWebSocketFrame && dropPongFrames) {
            readIfNeeded(ctx);
            return;
        }

        out.add(frame.retain());
    }

    private static void readIfNeeded(ChannelHandlerContext ctx) {
        if (!ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
    }

    @Nonnull
    public ChannelFuture close() {
        final var promise = context.newPromise();
        context.executor().execute(() -> {
            try {
                close(context, promise);
            } catch (Exception e) {
                promise.tryFailure(e);
            }
        });
        return promise;
    }

    @Override
    public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
        promise.addListener(future -> notifyClosed());
        if (closeStatus == null || !ctx.channel().isActive()) {
            ctx.close(promise);
        } else {
            if (closeSent == null) {
                write(ctx, new CloseWebSocketFrame(closeStatus), ctx.newPromise());
            }
            flush(ctx);
            applyCloseSentTimeout(ctx);
            closeSent.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    ctx.close(promise);
                }
            });
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        notifyClosed();
    }

    private void notifyClosed() {
        closeLock.lock();
        try {
            if (closedFlag == 1) {
                websocketMessageFactory.websocketDisconnectedEvent(this, websocketId);
            }
            closedFlag = 2;
        } finally {
            closeLock.unlock();
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (closeSent != null) {
            ReferenceCountUtil.release(msg);
            promise.setFailure(new ClosedChannelException());
        } else if (msg instanceof CloseWebSocketFrame) {
            closeSent(promise.unvoid());
            ctx.write(msg).addListener(new PromiseNotifier<Void, ChannelFuture>(false, closeSent));
        } else {
            ctx.write(msg, promise);
        }
    }

    void closeSent(ChannelPromise promise) {
        closeSent = promise;
    }

    private void applyCloseSentTimeout(ChannelHandlerContext ctx) {
        if (closeSent.isDone() || forceCloseTimeoutMillis < 0) {
            return;
        }

        final Future<?> timeoutTask = ctx.executor().schedule(new Runnable() {
            @Override
            public void run() {
                if (!closeSent.isDone()) {
                    closeSent.tryFailure(buildHandshakeException("send close frame timed out"));
                }
            }
        }, forceCloseTimeoutMillis, TimeUnit.MILLISECONDS);

        closeSent.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                timeoutTask.cancel(false);
            }
        });
    }

    /**
     * Returns a {@link WebSocketHandshakeException} that depends on which client or server pipeline
     * this handler belongs. Should be overridden in implementation otherwise a default exception is used.
     */
    protected WebSocketHandshakeException buildHandshakeException(String message) {
        return new WebSocketHandshakeException(message);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise)
            throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }
}
