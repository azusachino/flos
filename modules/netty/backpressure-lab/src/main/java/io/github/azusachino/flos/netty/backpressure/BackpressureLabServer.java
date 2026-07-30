package io.github.azusachino.flos.netty.backpressure;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public final class BackpressureLabServer {

    static final int CHUNK_SIZE = 8 * 1024;
    static final long TOTAL_BYTES = 8L * 1024 * 1024;
    static final int LOW_WATERMARK = 64 * 1024;
    static final int HIGH_WATERMARK = 128 * 1024;

    private final EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private final AtomicInteger pausedCount = new AtomicInteger();
    private final AtomicInteger resumedCount = new AtomicInteger();
    private Channel serverChannel;

    public int start(int port) throws InterruptedException {
        ServerBootstrap bootstrap =
                new ServerBootstrap()
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childOption(
                                ChannelOption.WRITE_BUFFER_WATER_MARK,
                                new WriteBufferWaterMark(LOW_WATERMARK, HIGH_WATERMARK))
                        .childHandler(
                                new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(SocketChannel channel) {
                                        channel
                                                .pipeline()
                                                .addLast(
                                                        new ThrottledProducerHandler(
                                                                CHUNK_SIZE, TOTAL_BYTES, pausedCount, resumedCount));
                                    }
                                });
        serverChannel = bootstrap.bind(port).sync().channel();
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public int pausedCount() {
        return pausedCount.get();
    }

    public int resumedCount() {
        return resumedCount.get();
    }

    public void awaitTermination() throws InterruptedException {
        serverChannel.closeFuture().sync();
    }

    public void stop() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.close().sync();
        }
        workerGroup.shutdownGracefully().sync();
        bossGroup.shutdownGracefully().sync();
    }

    public static void main(String[] args) throws InterruptedException {
        var server = new BackpressureLabServer();
        int port = server.start(9002);
        System.out.println("backpressure-lab producer listening on port " + port);
        System.out.println("Connect and read slowly to observe paused/resumed transitions.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.serverChannel.close()));
        try {
            server.awaitTermination();
        } finally {
            server.stop();
        }
    }
}
