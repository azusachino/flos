package io.github.azusachino.flos.netty.lifecycle;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class GracefulDisconnectHandler extends ChannelInboundHandlerAdapter {

    private final AtomicInteger idleDisconnectCount;

    public GracefulDisconnectHandler(AtomicInteger idleDisconnectCount) {
        this.idleDisconnectCount = idleDisconnectCount;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            idleDisconnectCount.incrementAndGet();
            ctx.writeAndFlush(Unpooled.copiedBuffer("idle-timeout\n", StandardCharsets.UTF_8))
                    .addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}
