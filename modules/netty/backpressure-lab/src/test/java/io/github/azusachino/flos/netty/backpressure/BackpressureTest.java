package io.github.azusachino.flos.netty.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackpressureTest {

    @Test
    void writabilityFlipsAtTheConfiguredWatermarks() {
        var channel = new EmbeddedChannel();
        channel
                .config()
                .setOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024, 2048));

        assertThat(channel.isWritable()).isTrue();

        // Register pending bytes without flushing: nothing drains them, simulating a consumer
        // that has stopped reading.
        for (int i = 0; i < 3; i++) {
            channel.write(Unpooled.wrappedBuffer(new byte[1024]));
        }
        assertThat(channel.isWritable()).isFalse();

        // Flushing on EmbeddedChannel completes every pending write immediately, standing in for
        // the moment a real consumer catches up and drains the buffer.
        channel.flush();
        assertThat(channel.isWritable()).isTrue();
    }

    @Test
    void throttledProducerPausesAndResumesAcrossTheFullPayload() {
        var pausedCount = new AtomicInteger();
        var resumedCount = new AtomicInteger();
        var handler = new ThrottledProducerHandler(1024, 32 * 1024, pausedCount, resumedCount);
        var channel = new EmbeddedChannel();
        channel
                .config()
                .setOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(4096, 8192));
        channel.pipeline().addLast(handler);

        handler.start();

        assertThat(handler.bytesSent()).isEqualTo(32 * 1024);
        assertThat(pausedCount.get()).isGreaterThan(0);
        assertThat(resumedCount.get()).isEqualTo(pausedCount.get());
        assertThat(channel.isWritable()).isTrue();
    }

    @Test
    void realSocketProducerPausesUnderARealSlowConsumer() throws Exception {
        var server = new BackpressureLabServer();
        int port = server.start(0);
        try (var socket = new Socket("localhost", port)) {
            // Deliberately delay reading so the server's outbound buffer cannot drain,
            // giving real OS-level backpressure a chance to build up.
            Thread.sleep(500);

            long totalRead = drainExpected(socket.getInputStream(), BackpressureLabServer.TOTAL_BYTES);

            assertThat(totalRead).isEqualTo(BackpressureLabServer.TOTAL_BYTES);
            assertThat(server.pausedCount()).isGreaterThan(0);
            assertThat(server.resumedCount()).isEqualTo(server.pausedCount());
        } finally {
            server.stop();
        }
    }

    private static long drainExpected(InputStream in, long expectedBytes) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        while (total < expectedBytes) {
            int read = in.read(buffer);
            if (read == -1) {
                break;
            }
            total += read;
        }
        return total;
    }
}
