package io.github.azusachino.flos.netty.backpressure;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ThrottledProducerHandler extends ChannelInboundHandlerAdapter {

    private final int chunkSize;
    private final long totalBytes;
    private final AtomicInteger pausedCount;
    private final AtomicInteger resumedCount;
    private final AtomicLong bytesSent = new AtomicLong();
    private ChannelHandlerContext ctx;

    public ThrottledProducerHandler(
            int chunkSize, long totalBytes, AtomicInteger pausedCount, AtomicInteger resumedCount) {
        this.chunkSize = chunkSize;
        this.totalBytes = totalBytes;
        this.pausedCount = pausedCount;
        this.resumedCount = resumedCount;
    }

    public long bytesSent() {
        return bytesSent.get();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        produce(ctx);
        ctx.fireChannelActive();
    }

    /** Starts production explicitly, for callers that add this handler after the channel is already active. */
    public void start() {
        produce(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (ctx.channel().isWritable()) {
            resumedCount.incrementAndGet();
            produce(ctx);
        } else {
            pausedCount.incrementAndGet();
        }
        ctx.fireChannelWritabilityChanged();
    }

    private void produce(ChannelHandlerContext ctx) {
        while (ctx.channel().isWritable() && bytesSent.get() < totalBytes) {
            ctx.write(Unpooled.wrappedBuffer(new byte[chunkSize]));
            bytesSent.addAndGet(chunkSize);
        }
        ctx.flush();
    }
}
