package io.github.azusachino.flos.netty.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LifecycleTest {

    @Test
    void idleStateHandlerClosesConnectionAfterConfiguredTimeout() {
        var idleDisconnectCount = new AtomicInteger();
        var channel =
                new EmbeddedChannel(
                        new IdleStateHandler(0, 0, 100, TimeUnit.MILLISECONDS),
                        new GracefulDisconnectHandler(idleDisconnectCount));

        channel.freezeTime();
        channel.advanceTimeBy(150, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();

        assertThat(idleDisconnectCount.get()).isEqualTo(1);
        assertThat(channel.isOpen()).isFalse();

        ByteBuf goodbye = channel.readOutbound();
        assertThat(goodbye.toString(StandardCharsets.UTF_8)).isEqualTo("idle-timeout\n");
        goodbye.release();
    }

    @Test
    void realSocketIdleTimeoutClosesTheConnection() throws Exception {
        var server = new LifecycleLabServer();
        int port = server.start(0, 200);
        try (var socket = new Socket("localhost", port)) {
            byte[] response = socket.getInputStream().readNBytes("idle-timeout\n".length());

            assertThat(new String(response, StandardCharsets.UTF_8)).isEqualTo("idle-timeout\n");
            assertThat(socket.getInputStream().read()).isEqualTo(-1);
            assertThat(server.idleDisconnectCount()).isEqualTo(1);
        } finally {
            server.stop();
        }
    }

    @Test
    void reconnectingClientRetriesAfterEachDisconnect() throws Exception {
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        Channel serverChannel = null;
        ReconnectingClient client = null;
        try {
            ServerBootstrap alwaysCloses =
                    new ServerBootstrap()
                            .group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .childHandler(
                                    new ChannelInitializer<SocketChannel>() {
                                        @Override
                                        protected void initChannel(SocketChannel ch) {
                                            ch.close();
                                        }
                                    });
            serverChannel = alwaysCloses.bind(0).sync().channel();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

            client = new ReconnectingClient("localhost", port, 50);
            client.start();

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (client.connectAttempts() < 3 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }

            assertThat(client.connectAttempts()).isGreaterThanOrEqualTo(3);
        } finally {
            if (client != null) {
                client.stop();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
