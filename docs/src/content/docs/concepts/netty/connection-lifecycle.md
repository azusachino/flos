---
title: Netty Connection Lifecycle and Resilience
description: Detect a silent connection with IdleStateHandler, close it gracefully, and recover automatically with a reconnecting client.
created: 2026-07-30 23:04
modified: 2026-07-30 23:04
type: concept
status: active
maturity: developing
tags:
    - netty
    - connection-lifecycle
    - resilience
source: https://netty.io/4.2/api/io/netty/handler/timeout/IdleStateHandler.html
---

# Netty Connection Lifecycle and Resilience

The [backpressure lab](../backpressure/) assumed a connection stays open and healthy. Real connections don't: a peer can go silent without ever sending a TCP `FIN`, and a client can lose its server mid-session. This lab's only question is:

> How does one side detect a silent peer, close cleanly instead of just vanishing, and how does the other side recover on its own?

```mermaid
flowchart LR
    Silence["No reads or writes for the configured timeout"] --> Idle["IdleStateHandler fires IdleStateEvent"]
    Idle --> Goodbye["Handler writes a final message, then closes"]
    Goodbye --> Gone["Client observes channelInactive"]
    Gone --> Retry["Client schedules a reconnect after backoff"]
```

## Source code map

The example is not only documentation. Its complete source is under `modules/netty/lifecycle-lab`:

| File | What to learn from it |
| --- | --- |
| [`LifecycleLabServer.java`](https://github.com/azusachino/flos/blob/main/modules/netty/lifecycle-lab/src/main/java/io/github/azusachino/flos/netty/lifecycle/LifecycleLabServer.java) | Wires `IdleStateHandler` in front of the disconnect handler |
| [`GracefulDisconnectHandler.java`](https://github.com/azusachino/flos/blob/main/modules/netty/lifecycle-lab/src/main/java/io/github/azusachino/flos/netty/lifecycle/GracefulDisconnectHandler.java) | Turns an idle timeout into a final message, then a close |
| [`ReconnectingClient.java`](https://github.com/azusachino/flos/blob/main/modules/netty/lifecycle-lab/src/main/java/io/github/azusachino/flos/netty/lifecycle/ReconnectingClient.java) | Detects `channelInactive` and reconnects after a fixed backoff |
| [`LifecycleTest.java`](https://github.com/azusachino/flos/blob/main/modules/netty/lifecycle-lab/src/test/java/io/github/azusachino/flos/netty/lifecycle/LifecycleTest.java) | Proves the idle timeout deterministically, over a real socket, and proves the reconnect loop |

## Detecting silence: IdleStateHandler

```java
new IdleStateHandler(0, 0, idleTimeoutMillis, TimeUnit.MILLISECONDS)
```

`IdleStateHandler`'s three durations are independent: reader-idle (no inbound data), writer-idle (no outbound data), and all-idle (neither direction active). Passing `0` disables that check. This lab only cares whether the connection has gone completely silent, so reader- and writer-idle are disabled and only `allIdleTime` is set.

When the timeout elapses, `IdleStateHandler` does not close anything itself — it fires an `IdleStateEvent` through the pipeline as a user event, leaving the decision to the application:

```java
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
```

## Closing gracefully, not abruptly

`ctx.close()` alone can race with a pending write: if the write hasn't reached the socket yet, an immediate close can drop it. `.addListener(ChannelFutureListener.CLOSE)` instead closes the channel only after that specific write's future completes — the peer is guaranteed to receive `"idle-timeout\n"` before the connection ends, rather than seeing the connection simply disappear.

## Proving idle detection three ways

```java
channel.freezeTime();
channel.advanceTimeBy(150, TimeUnit.MILLISECONDS);
channel.runScheduledPendingTasks();

assertThat(idleDisconnectCount.get()).isEqualTo(1);
assertThat(channel.isOpen()).isFalse();
```

`idleStateHandlerClosesConnectionAfterConfiguredTimeout` never actually waits. `EmbeddedChannel.freezeTime()` and `advanceTimeBy()` move its internal clock forward instantly, so a 100 ms idle timeout can be proven in microseconds of real test time, deterministically, on any machine regardless of load.

`realSocketIdleTimeoutClosesTheConnection` proves the same behavior over an actual socket with a real 200 ms wait: the client reads exactly the `"idle-timeout\n"` message, then confirms the stream reaches end-of-file. Two different mechanisms, two different guarantees — the virtual clock proves the handler's logic; the real socket proves the whole pipeline actually closes a real connection.

## Recovering: a client that reconnects itself

```java
@Override
public void channelInactive(ChannelHandlerContext ctx) {
    disconnectCount.incrementAndGet();
    scheduleReconnect();
    ctx.fireChannelInactive();
}
```

```java
private void scheduleReconnect() {
    if (stopping) {
        return;
    }
    group.schedule(this::connect, backoffMillis, TimeUnit.MILLISECONDS);
}
```

`ReconnectingClient` treats every disconnection the same way, whether caused by an idle timeout, a server restart, or a network blip: notice `channelInactive`, wait a fixed backoff, try again. `reconnectingClientRetriesAfterEachDisconnect` proves this against a real server that accepts and immediately closes every connection, then polls until at least three connection attempts have happened — proving the retry loop actually runs repeatedly rather than stopping after the first failure.

This lab uses a fixed backoff for clarity. A production client would typically add jitter and a growing delay to avoid many clients reconnecting in lockstep against a recovering server — noted here as a real gap, not implemented, to keep the lesson focused on the retry mechanism itself.

## What's proven, what's not

| Test | Proves |
| --- | --- |
| `idleStateHandlerClosesConnectionAfterConfiguredTimeout` | The idle timeout and graceful-close logic are correct, independent of real time |
| `realSocketIdleTimeoutClosesTheConnection` | The same logic closes a real connection after a real elapsed timeout |
| `reconnectingClientRetriesAfterEachDisconnect` | The client detects disconnection and retries repeatedly, not just once |

## Run the lab

```sh
make netty-lifecycle
```

Connect and stay silent:

```sh
nc localhost 9003
```

After 10 seconds, expect:

```text
idle-timeout
```

followed by the connection closing.

## Exercises

1. Set `readerIdleTime` instead of `allIdleTime` and send one byte just before the timeout. Does the connection survive, and why does that differ from `allIdleTime`?
2. Remove `.addListener(ChannelFutureListener.CLOSE)` and call `ctx.close()` directly instead. Design a scenario where the client might not receive `"idle-timeout\n"`.
3. Add jitter to `ReconnectingClient`'s backoff and adjust the test's polling deadline to account for the added variance.
4. `reconnectingClientRetriesAfterEachDisconnect` uses a server that closes every connection immediately. Change it to close only the first two connections and keep the third open — what does the test need to assert differently?

## What's next

These four labs cover a single connection end to end: accept it, frame its messages, respect its capacity, and outlive its failures. Later topics build multi-connection concerns — routing, connection pooling, and protocol-specific patterns — on top of this foundation.
