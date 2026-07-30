package io.github.azusachino.flos.netty.lifecycle;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class LifecycleLabServer {

    static final long IDLE_TIMEOUT_MILLIS = 300;

    private final EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private final AtomicInteger idleDisconnectCount = new AtomicInteger();
    private Channel serverChannel;

    public int start(int port, long idleTimeoutMillis) throws InterruptedException {
        ServerBootstrap bootstrap =
                new ServerBootstrap()
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(
                                new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    protected void initChannel(SocketChannel channel) {
                                        channel
                                                .pipeline()
                                                .addLast(
                                                        new IdleStateHandler(
                                                                0, 0, idleTimeoutMillis, TimeUnit.MILLISECONDS),
                                                        new GracefulDisconnectHandler(idleDisconnectCount));
                                    }
                                });
        serverChannel = bootstrap.bind(port).sync().channel();
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public int idleDisconnectCount() {
        return idleDisconnectCount.get();
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
        var server = new LifecycleLabServer();
        int port = server.start(9003, TimeUnit.SECONDS.toMillis(10));
        System.out.println("lifecycle-lab server listening on port " + port);
        System.out.println("Connect and stay silent for 10 seconds to be disconnected as idle.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.serverChannel.close()));
        try {
            server.awaitTermination();
        } finally {
            server.stop();
        }
    }
}
