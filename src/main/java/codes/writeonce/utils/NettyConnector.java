package codes.writeonce.utils;

import codes.writeonce.disruptor.Connector;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class NettyConnector implements Connector {

    private static final boolean LOG_DATA = Boolean.getBoolean("codes.writeonce.utils.NettyConnector.LOG_DATA");

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Nonnull
    private final WebsocketMessageFactory websocketMessageFactory;

    @Nonnull
    private final Mapping mapping;

    @Nonnull
    private final String bindAddress;

    private final int bindPort;

    @Nullable
    private final SslContext sslCtx;

    private NioEventLoopGroup acceptorGroup;

    private NioEventLoopGroup workerGroup;

    private ChannelFuture closeFuture;

    public NettyConnector(
            @Nonnull WebsocketMessageFactory websocketMessageFactory,
            @Nonnull Mapping mapping,
            @Nonnull Path certChainFilePath,
            @Nonnull Path certKeyFilePath,
            @Nonnull String bindAddress,
            int bindPort
    ) throws Exception {
        this(
                websocketMessageFactory,
                mapping,
                bindAddress,
                bindPort,
                SslContextBuilder
                        .forServer(
                                Files.newInputStream(certChainFilePath),
                                Files.newInputStream(certKeyFilePath)
                        )
                        .sslProvider(SslProvider.OPENSSL_REFCNT)
                        .build()
        );
    }

    public NettyConnector(
            @Nonnull WebsocketMessageFactory websocketMessageFactory,
            @Nonnull Mapping mapping,
            @Nonnull String bindAddress,
            int bindPort
    ) throws Exception {
        this(
                websocketMessageFactory,
                mapping,
                bindAddress,
                bindPort,
                null
        );
    }

    private NettyConnector(
            @Nonnull WebsocketMessageFactory websocketMessageFactory,
            @Nonnull Mapping mapping,
            @Nonnull String bindAddress,
            int bindPort,
            @Nullable SslContext sslCtx
    ) {
        this.websocketMessageFactory = websocketMessageFactory;
        this.mapping = mapping;
        this.bindAddress = bindAddress;
        this.bindPort = bindPort;
        this.sslCtx = sslCtx;
    }

    public synchronized void start() throws InterruptedException, ExecutionException {

        if (closeFuture != null) {
            return;
        }

        try {
            acceptorGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup(1);
            final var b = new ServerBootstrap();
            b.group(acceptorGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            final var p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc()));
                            }
                            if (LOG_DATA) {
                                p.addLast(new LoggingHandler(LogLevel.INFO));
                            }
                            p.addLast(new HttpServerCodec());
                            p.addLast(new WebSocketServerProtocolHandshakeHandler(websocketMessageFactory));
                            p.addLast(new HttpChunkContentCompressor());
                            p.addLast(new ChunkedWriteHandler());
                            p.addLast(new CustomHttpServerHandler(mapping));
                            p.addLast(new TailHandler());
                        }
                    });

            closeFuture = b.bind(bindAddress, bindPort).sync().channel().closeFuture();
        } catch (Throwable e) {
            logger.error("Failed to start Netty connector", e);
            shutdown().get();
            throw e;
        }
    }

    @Nonnull
    @Override
    public synchronized CompletableFuture<Void> shutdown() {

        logger.info("Cleaning up Netty connector");

        if (acceptorGroup != null) {
            acceptorGroup.shutdownGracefully();
            acceptorGroup = null;
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        if (closeFuture == null) {
            return CompletableFuture.completedFuture(null);
        }

        final var nettyFuture = new CompletableFuture<Void>();

        closeFuture.addListener(future -> {
            if (future.isSuccess()) {
                logger.info("Netty connector cleaned up");
                nettyFuture.complete(null);
            } else {
                final var cause = future.cause();
                logger.error("Netty connector cleanup failed", cause);
                nettyFuture.completeExceptionally(cause);
            }
        });

        closeFuture = null;
        return nettyFuture;
    }
}
