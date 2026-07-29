# Architecture

Flos is a Maven reactor organized into top-level technology aggregators. Each technology may contain a reusable semantics module and one or more runnable labs.

```text
flos
├── modules/flink
│   ├── operators       reusable transformations and unit tests
│   ├── operator-lab    executable bounded Flink job
│   └── pipeline-lab    Kafka to operators to MySQL job
├── environments/flink
│   ├── compose.yaml    local session cluster
│   └── kubernetes      operator-managed session cluster
├── scripts             uv-managed automation
├── vendor              pinned upstream source submodules
└── docs                Starlight tutorial
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

Maven proves operator semantics and packages the jobs. Podman Compose provides a JobManager, TaskManager, Kafka broker, and MySQL database. The bounded Python smoke test waits for the cluster, submits the packaged operator artifact, and verifies the terminal job state through Flink's REST API. The separate pipeline lab applies the same operators to a Kafka source and MySQL JDBC sink.

The `vendor/` directory is not part of the build. Its shallow submodules pin the Flink and Kubernetes Operator source trees for fast code navigation and local cross-reference; `ignore = all` prevents incidental upstream edits from polluting this project's status.

The version baseline is Flink 2.2.1 on Java 17 with Flink Kubernetes Operator 1.15.0 for Kubernetes deployments. The Compose environment covers Flink job behavior, while the Kubernetes manifest exercises the operator's `FlinkDeployment` reconciliation contract.
