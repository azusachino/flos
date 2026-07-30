package io.github.azusachino.flos.netty.lifecycle;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReconnectingClient {

    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private final AtomicInteger connectAttempts = new AtomicInteger();
    private final AtomicInteger disconnectCount = new AtomicInteger();
    private final String host;
    private final int port;
    private final long backoffMillis;
    private volatile boolean stopping;

    public ReconnectingClient(String host, int port, long backoffMillis) {
        this.host = host;
        this.port = port;
        this.backoffMillis = backoffMillis;
    }

    public void start() {
        connect();
    }

    public int connectAttempts() {
        return connectAttempts.get();
    }

    public int disconnectCount() {
        return disconnectCount.get();
    }

    public void stop() {
        stopping = true;
        group.shutdownGracefully();
    }

    private void connect() {
        if (stopping) {
            return;
        }
        connectAttempts.incrementAndGet();
        var bootstrap =
                new Bootstrap()
                        .group(group)
                        .channel(NioSocketChannel.class)
                        .handler(
                                new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(SocketChannel channel) {
                                        channel
                                                .pipeline()
                                                .addLast(
                                                        new ChannelInboundHandlerAdapter() {
                                                            @Override
                                                            public void channelInactive(
                                                                    ChannelHandlerContext ctx) {
                                                                disconnectCount.incrementAndGet();
                                                                scheduleReconnect();
                                                                ctx.fireChannelInactive();
                                                            }
                                                        });
                                    }
                                });
        bootstrap
                .connect(host, port)
                .addListener(
                        future -> {
                            if (!future.isSuccess()) {
                                scheduleReconnect();
                            }
                        });
    }

    private void scheduleReconnect() {
        if (stopping) {
            return;
        }
        group.schedule(this::connect, backoffMillis, TimeUnit.MILLISECONDS);
    }
}
