# ClickHouse sink deep dive

- [x] Human review of the scope and tuning decisions.
- [x] Implement the smallest working typed sink.
- [x] Verify the connector against ClickHouse 26.x locally.
- [x] Add replay/duplicate evidence.
- [x] Publish the tutorial, deep dive, and learning links.

## Follow-up experiments

- [ ] Compare HTTP with the native client under a representative workload.
- [ ] Exercise distributed Flink lateness/watermark recovery and connector checkpoint replay.
- [x] Exercise local late-arrival/correction, backfill, skew, search/export contention, and CSV cutoff consistency.
