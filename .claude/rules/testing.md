---
paths:
  - "**/src/test/**"
  - "scripts/**/*_test.py"
---

# Testing conventions

- Test operator/handler semantics deterministically: fixed values and timestamps, or a virtual clock (for example `EmbeddedChannel.freezeTime()` / `advanceTimeBy()`).
- Use runtime smoke tests only for boundaries that require a real cluster or a real socket.
- A green unit test does not prove job submission, scheduling, connection acceptance, or completion.
