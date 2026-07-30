package io.github.azusachino.flos.netty.eventloop;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public final class EventLoopLabServer {

    private final EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    private final AtomicInteger activeConnections = new AtomicInteger();
    private Channel serverChannel;

    public int start(int port) throws InterruptedException {
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
                                                        new UppercaseInboundHandler(),
                                                        new EchoServerHandler(activeConnections));
                                    }
                                });
        serverChannel = bootstrap.bind(port).sync().channel();
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public int activeConnections() {
        return activeConnections.get();
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
        var server = new EventLoopLabServer();
        int port = server.start(9000);
        System.out.println("event-loop-lab echo server listening on port " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.serverChannel.close()));
        try {
            server.awaitTermination();
        } finally {
            server.stop();
        }
    }
}
