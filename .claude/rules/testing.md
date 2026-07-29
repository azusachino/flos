---
paths:
  - "**/src/test/**"
  - "scripts/**/*_test.py"
---

# Testing conventions

- Test operator semantics deterministically with fixed values and timestamps.
- Use runtime smoke tests only for boundaries that require a real Flink cluster.
- A green unit test does not prove job submission, scheduling, or completion.
