package io.github.azusachino.flos.netty.framing;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FramingCodecTest {

    @Test
    void rawPipelineCoalescesTwoMessagesIntoOneRead() {
        List<ByteBuf> received = new ArrayList<>();
        var channel = new EmbeddedChannel(capturingHandler(received));

        ByteBuf combined =
                Unpooled.wrappedBuffer(
                        "first".getBytes(StandardCharsets.UTF_8),
                        "second".getBytes(StandardCharsets.UTF_8));
        channel.writeInbound(combined);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).toString(StandardCharsets.UTF_8)).isEqualTo("firstsecond");
        received.forEach(ByteBuf::release);
    }

    @Test
    void lengthFieldFramingSplitsCoalescedMessages() {
        List<String> received = new ArrayList<>();
        var channel = frameDecodingChannel(received);

        ByteBuf combined = Unpooled.buffer();
        writeFrame(combined, "first");
        writeFrame(combined, "second");
        channel.writeInbound(combined);

        assertThat(received).containsExactly("first", "second");
    }

    @Test
    void lengthFieldFramingBuffersASplitMessage() {
        List<String> received = new ArrayList<>();
        var channel = frameDecodingChannel(received);

        ByteBuf full = Unpooled.buffer();
        writeFrame(full, "hello-world");
        byte[] bytes = new byte[full.readableBytes()];
        full.readBytes(bytes);
        full.release();

        int splitPoint = 6; // inside the 4-byte length header plus two body bytes
        channel.writeInbound(Unpooled.wrappedBuffer(bytes, 0, splitPoint));
        assertThat(received).isEmpty();

        channel.writeInbound(Unpooled.wrappedBuffer(bytes, splitPoint, bytes.length - splitPoint));
        assertThat(received).containsExactly("hello-world");
    }

    @Test
    void realSocketRoundTripSurvivesCoalescedWrites() throws Exception {
        var server = new FramingLabServer();
        int port = server.start(0);
        try (var socket = new Socket("localhost", port)) {
            var out = new DataOutputStream(socket.getOutputStream());
            writeFrame(out, "first");
            writeFrame(out, "second");
            out.flush();

            var in = new DataInputStream(socket.getInputStream());
            assertThat(readFrame(in)).isEqualTo("first");
            assertThat(readFrame(in)).isEqualTo("second");
        } finally {
            server.stop();
        }
    }

    private static ChannelInboundHandlerAdapter capturingHandler(List<ByteBuf> received) {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                received.add((ByteBuf) msg);
            }
        };
    }

    private static EmbeddedChannel frameDecodingChannel(List<String> received) {
        return new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(
                        FramingLabServer.MAX_FRAME_LENGTH, 0, FramingLabServer.LENGTH_FIELD_LENGTH, 0, FramingLabServer.LENGTH_FIELD_LENGTH),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf frame = (ByteBuf) msg;
                        received.add(frame.toString(StandardCharsets.UTF_8));
                        frame.release();
                    }
                });
    }

    private static void writeFrame(ByteBuf target, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        target.writeInt(bytes.length);
        target.writeBytes(bytes);
    }

    private static void writeFrame(DataOutputStream out, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readFrame(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
