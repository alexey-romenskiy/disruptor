package codes.writeonce.utils;

import codes.writeonce.disruptor.Disruptor;
import codes.writeonce.disruptor.DisruptorEntry;
import codes.writeonce.disruptor.Event;
import codes.writeonce.disruptor.Processor;
import codes.writeonce.disruptor.RingBuffer;
import codes.writeonce.disruptor.Sender;
import codes.writeonce.disruptor.ShutdownEvent;
import codes.writeonce.disruptor.Slot;
import codes.writeonce.disruptor.Slots;
import codes.writeonce.disruptor.Worker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.stream.ChunkedInput;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Date;
import java.util.concurrent.locks.LockSupport;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.DATE;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaderNames.ORIGIN;
import static io.netty.handler.codec.http.HttpHeaderNames.PRAGMA;
import static io.netty.handler.codec.http.HttpHeaderNames.VARY;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class NettyWorker implements Worker {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Nonnull
    private final Disruptor disruptor;

    @Nonnull
    private final RingBuffer<DisruptorEntry<Event>> ringBuffer;

    @Nonnull
    private final Sender<Event> sender;

    @Nonnull
    private final Processor processor;

    @Nonnull
    private final Slot<NettyRequestContext> requestSlot;

    @Nonnull
    private final Slot<WebSocketProtocolHandler> websocketSlot;

    public NettyWorker(
            @Nonnull Disruptor disruptor,
            @Nonnull RingBuffer<DisruptorEntry<Event>> ringBuffer,
            @Nonnull Sender<Event> sender,
            @Nonnull Slots slots,
            @Nonnull Processor processor
    ) {
        this.disruptor = disruptor;
        this.ringBuffer = ringBuffer;
        this.sender = sender;
        this.processor = processor;
        requestSlot = slots.slot(NettyRequestSlotKey.INSTANCE);
        websocketSlot = slots.slot(NettyWebSocketSlotKey.INSTANCE);
    }

    @Override
    public void run() {

        long sequence = 0;

        while (true) {
            var n = processor.claimBlocking();
            if (disruptor.isTerminate()) {
                break;
            }
            if (n == 0) {
                LockSupport.park();
            } else {
                do {
                    final var entry = ringBuffer.get(sequence);
                    final var event = entry.event;
                    if (event instanceof ShutdownEvent) {
                        sequence += n;
                        processor.publish(sequence);
                        while (!disruptor.isTerminate()) {
                            n = processor.claim();
                            if (n != 0) {
                                sequence += n;
                                processor.publish(sequence);
                            }
                        }
                        return;
                    }
                    if (event instanceof final NettyEvent nettyEvent) {
                        process(nettyEvent, requestSlot.get(entry.slots));
                    } else if (event instanceof final WebsocketConnectedEvent nettyEvent) {
                        process(nettyEvent);
                    } else if (event instanceof final WebsocketDisconnectedEvent nettyEvent) {
                        process(nettyEvent);
                    } else if (event instanceof final WebsocketMessageEvent nettyEvent) {
                        process(nettyEvent, websocketSlot.get(entry.slots));
                    } else if (event instanceof final WebsocketMessageSentEvent nettyEvent) {
                        process(nettyEvent);
                    } else if (event instanceof final WebsocketMessageNotSentEvent nettyEvent) {
                        process(nettyEvent);
                    }
                    sequence++;
                } while (--n != 0);
                processor.publish(sequence);
            }
        }
    }

    private void process(WebsocketMessageNotSentEvent nettyEvent) {
        logger.info("WebsocketMessageNotSentEvent");
    }

    private void process(WebsocketMessageSentEvent nettyEvent) {
        logger.info("WebsocketMessageSentEvent");
    }

    private void process(WebsocketMessageEvent nettyEvent, WebSocketProtocolHandler handler) {
        logger.info("WebsocketMessageEvent");
        final var context = handler.context;
        context.writeAndFlush(new TextWebSocketFrame("Message recieved : " + nettyEvent.getText()))
                .addListener(future -> {
                    if (future.isSuccess()) {
                        sender.send(System.nanoTime(), new WebsocketMessageSentEvent(handler.websocketId));
                    } else {
                        sender.send(System.nanoTime(), new WebsocketMessageNotSentEvent(handler.websocketId));
                        logger.error("Message not sent", future.cause());
                    }
                });
        handler.close();
    }

    private void process(WebsocketDisconnectedEvent nettyEvent) {
        logger.info("WebsocketDisconnectedEvent");
    }

    private void process(WebsocketConnectedEvent nettyEvent) {
        logger.info("WebsocketConnectedEvent");
    }

    private void process(@Nonnull NettyEvent event, @Nonnull NettyRequestContext nettyRequestContext) {

        final var context = nettyRequestContext.getContext();
        final var request = nettyRequestContext.getRequest();
        final var keepAlive = HttpUtil.isKeepAlive(request);
        final var response = new DefaultHttpResponse(HTTP_1_1, OK);
        final var headers = response.headers();
        headers.set(CONTENT_TYPE, "application/json; charset=UTF-8");
        headers.set("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
        headers.set(DATE, DateFormatter.format(new Date()));

        final var origin = request.headers().get(ORIGIN);
        headers.set(ACCESS_CONTROL_ALLOW_ORIGIN, origin == null ? "*" : origin);
        headers.set(VARY, "Origin");
        headers.set(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

        headers.set(CACHE_CONTROL, "max-age=0, no-cache, no-store, must-revalidate");
        headers.set(PRAGMA, "no-cache");
        headers.set(EXPIRES, "Wed, 11 Jan 1984 05:00:00 GMT");

        if (!keepAlive) {
            headers.set(CONNECTION, CLOSE);
        } else if (request.protocolVersion().equals(HTTP_1_0)) {
            headers.set(CONNECTION, KEEP_ALIVE);
        }

        HttpUtil.setTransferEncodingChunked(response, true);

        context.write(response);

        final var writer = new JsonWriter();
        writer.start(new JsonObject().put("foo", "bar"));

        final var future = context.writeAndFlush(new HttpChunkedInput(new ChunkedInput<>() {

            private static final int BUFFER_SIZE = 0x10000;

            private boolean endOfInput;

            @Override
            public boolean isEndOfInput() {
                return endOfInput;
            }

            @Override
            public void close() {
                // empty
            }

            @Override
            public ByteBuf readChunk(ChannelHandlerContext ctx) {
                return readChunk(ctx.alloc());
            }

            @Override
            public ByteBuf readChunk(ByteBufAllocator allocator) {

                if (endOfInput) {
                    return null;
                }

                final var buffer = allocator.buffer(BUFFER_SIZE, BUFFER_SIZE);
                final var writerIndex = buffer.writerIndex();
                int length = 0;

                for (final var byteBuffer : buffer.nioBuffers(writerIndex, buffer.writableBytes())) {
                    final var start = byteBuffer.position();
                    endOfInput = !writer.write(byteBuffer);
                    length += byteBuffer.position() - start;
                    if (endOfInput) {
                        break;
                    }
                }

                buffer.writerIndex(writerIndex + length);
                return buffer;
            }

            @Override
            public long length() {
                return -1;
            }

            @Override
            public long progress() {
                return 0;
            }
        }));

        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
