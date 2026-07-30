---
title: Netty
description: Learn Netty's event-driven networking model from a single connection outward, four labs at a time.
created: 2026-07-30 23:11
modified: 2026-07-30 23:11
type: map
status: active
maturity: developing
tags:
    - netty
    - network-programming
source: https://netty.io/wiki/user-guide-for-4.x.html
---

# Netty

Netty is an asynchronous, event-driven network application framework. It is also the transport underneath much of the distributed-systems ecosystem this project studies: Flink's own data-plane shuffle service is built on it, and so are gRPC, Cassandra's driver, and many other RPC and messaging systems.

These four labs teach a single TCP connection end to end, in the order a real connection actually experiences them:

1. [Event Loop and Channel Pipeline](./event-loop/) — how bytes reach handler code, and in what order.
2. [Framing and Codecs](./framing/) — why one write does not mean one read, and how to recover message boundaries.
3. [Backpressure and Flow Control](./backpressure/) — what happens when a consumer falls behind a producer.
4. [Connection Lifecycle and Resilience](./connection-lifecycle/) — detecting a silent peer, closing cleanly, and reconnecting.

Each lab pairs a small, complete server under `modules/netty` with tests that separate what an `EmbeddedChannel` proves deterministically from what only a real socket can prove. Later topics can build multi-connection concerns — routing, pooling, protocol-specific handlers — on top of this foundation.
