package io.github.azusachino.flos.netty.eventloop;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EventLoopPipelineTest {

    @Test
    void pipelineOrderUppercasesBeforeEchoing() {
        var activeConnections = new AtomicInteger();
        var channel =
                new EmbeddedChannel(
                        new UppercaseInboundHandler(), new EchoServerHandler(activeConnections));

        channel.writeInbound(Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8));

        ByteBuf outbound = channel.readOutbound();
        assertThat(outbound.toString(StandardCharsets.UTF_8)).isEqualTo("HELLO");
        outbound.release();
    }

    @Test
    void channelActiveAndInactiveTrackConnectionCount() {
        var activeConnections = new AtomicInteger();
        var channel = new EmbeddedChannel(new EchoServerHandler(activeConnections));

        assertThat(activeConnections.get()).isEqualTo(1);

        channel.close();

        assertThat(activeConnections.get()).isEqualTo(0);
    }

    @Test
    void realSocketRoundTripProvesTheEventLoopAcceptsAndEchoes() throws Exception {
        var server = new EventLoopLabServer();
        int port = server.start(0);
        try (var socket = new Socket("localhost", port)) {
            socket.getOutputStream().write("hi".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();

            byte[] response = socket.getInputStream().readNBytes(2);

            assertThat(new String(response, StandardCharsets.UTF_8)).isEqualTo("HI");
            assertThat(server.activeConnections()).isEqualTo(1);
        } finally {
            server.stop();
        }
    }
}
