package io.github.azusachino.flos.netty.eventloop;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class UppercaseInboundHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf) {
            String text = buf.toString(StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
            buf.release();
            ctx.fireChannelRead(Unpooled.copiedBuffer(text, StandardCharsets.UTF_8));
        } else {
            ctx.fireChannelRead(msg);
        }
    }
}
