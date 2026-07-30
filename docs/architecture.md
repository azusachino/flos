# Architecture

Flos is a Maven reactor organized into top-level technology aggregators. Each technology may contain a reusable semantics module and one or more runnable labs.

```text
flos
├── modules/flink
│   ├── operators       reusable transformations and unit tests
│   ├── operator-lab    executable bounded Flink job
│   ├── event-time      reusable billing event and window functions
│   ├── event-time-lab  in-memory windows and watermarks lesson
│   ├── checkpoint-recovery-lab
│   │                   checkpoint failure recovery and savepoint upgrade proof
│   └── pipeline-lab    Kafka to operators to MySQL job
├── environments/flink
│   ├── compose.yaml    local session cluster
│   └── kubernetes      operator-managed session cluster
├── scripts             uv-managed automation
├── vendor              pinned upstream source submodules
└── docs
    ├── concepts        executable Starlight tutorials
    └── operations      Flink ownership and maintenance handbook
```

The Flink operator path is:

```text
PurchaseEvent
  -> ValidPurchaseFilter
  -> NormalizePurchase
  -> keyBy(customerId)
  -> RunningSpend
  -> print sink
```

Maven proves operator semantics and packages the jobs. The event-time lab runs locally from a bounded collection so windows and watermarks can be learned without infrastructure. The checkpoint-recovery lab runs embedded MiniClusters for two distinct experiments: automatic recovery after a completed checkpoint, and a planned revision A to revision B upgrade through a canonical savepoint while rescaling the window operator. Podman Compose provides a JobManager, TaskManager, Kafka broker, and MySQL database. The bounded Python smoke test waits for the cluster, submits the packaged operator artifact, and verifies the terminal job state through Flink's REST API. The separate pipeline lab applies the same operators to a Kafka source and MySQL JDBC sink.

The `vendor/` directory is not part of the build. Its shallow submodules pin the Flink and Kubernetes Operator source trees for fast code navigation and local cross-reference; `ignore = all` prevents incidental upstream edits from polluting this project's status.

The version baseline is Flink 2.2.1 on Java 17 with Flink Kubernetes Operator 1.15.0 for Kubernetes deployments. The Compose environment covers Flink job behavior, while the Kubernetes manifest exercises the operator's `FlinkDeployment` reconciliation contract.

The operations handbook deliberately separates example configuration from verified repository behavior. Its snippets describe the target production contract; they become project evidence only after the corresponding connector integration test, failure drill, restore drill, or environment observation exists.
