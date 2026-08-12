CONCEPT ?= flink
TOPIC ?=
TITLE ?=
FLINK_COMPOSE := environments/flink/compose.yaml
CLICKHOUSE_COMPOSE := environments/clickhouse/compose.yaml
NETTY_COMPOSE := environments/netty/compose.yaml

.PHONY: setup fmt fmt-check lint test check validate clean docs docs-check topic-new concept-check concept-test flink-event-time flink-state-ttl flink-restart-strategy flink-slot-sharing flink-recovery flink-savepoint-upgrade flink-recovery-package flink-package flink-pipeline-package flink-clickhouse-package clickhouse-up clickhouse-sink-smoke clickhouse-workload clickhouse-down flink-up flink-smoke flink-billing-smoke flink-billing-recovery flink-observability-smoke flink-down netty-event-loop netty-framing netty-backpressure netty-lifecycle netty-up netty-smoke netty-down

setup:
	uv sync

fmt:
	uv run ruff format scripts

fmt-check:
	uv run ruff format --check scripts

lint:
	uv run ruff check scripts

test:
	mvn test

check: fmt-check lint test

validate: check docs-check flink-recovery-package flink-package flink-pipeline-package

clean:
	mvn clean
	rm -rf site

docs:
	NO_MKDOCS_2_WARNING=true uv run mkdocs serve

docs-check:
	NO_MKDOCS_2_WARNING=true uv run mkdocs build --strict
	uv run scripts/validate_docs_frontmatter.py

topic-new:
	@test -n "$(TOPIC)" || (echo "TOPIC is required" >&2; exit 2)
	@test -n "$(TITLE)" || (echo "TITLE is required" >&2; exit 2)
	uv run scripts/new_topic.py --concept "$(CONCEPT)" --topic "$(TOPIC)" --title "$(TITLE)"

concept-check:
	mvn -pl "modules/$(CONCEPT)" -am test

concept-test: concept-check

flink-event-time:
	mvn -pl modules/flink/event-time-lab -am package
	java -jar modules/flink/event-time-lab/target/event-time-lab.jar

flink-state-ttl:
	mvn -pl modules/flink/state-ttl-lab -am package
	java -jar modules/flink/state-ttl-lab/target/state-ttl-lab.jar

flink-restart-strategy:
	mvn -pl modules/flink/restart-strategy-lab -am package
	java -jar modules/flink/restart-strategy-lab/target/restart-strategy-lab.jar

flink-slot-sharing:
	mvn -pl modules/flink/slot-sharing-lab -am package
	java -jar modules/flink/slot-sharing-lab/target/slot-sharing-lab.jar

flink-recovery:
	mvn -pl modules/flink/checkpoint-recovery-lab -am -Dtest=CheckpointRecoveryLabTest -Dsurefire.failIfNoSpecifiedTests=false test

flink-savepoint-upgrade:
	mvn -pl modules/flink/checkpoint-recovery-lab -am -Dtest=SavepointUpgradeLabTest -Dsurefire.failIfNoSpecifiedTests=false test

flink-recovery-package:
	mvn -pl modules/flink/checkpoint-recovery-lab -am package

flink-package:
	mvn -pl modules/flink/operator-lab -am package

flink-pipeline-package:
	mvn -pl modules/flink/pipeline-lab -am package

flink-clickhouse-package:
	mvn -pl modules/flink/clickhouse-sink-lab -am package

clickhouse-up:
	podman compose -f "$(CLICKHOUSE_COMPOSE)" up -d

clickhouse-sink-smoke: flink-clickhouse-package clickhouse-up
	uv run scripts/clickhouse_sink_smoke.py

clickhouse-workload: clickhouse-up
	uv run scripts/clickhouse_workload.py

clickhouse-down:
	podman compose -f "$(CLICKHOUSE_COMPOSE)" down

flink-up: flink-package flink-pipeline-package
	podman compose -f "$(FLINK_COMPOSE)" up -d

flink-smoke:
	uv run scripts/flink_smoke.py

flink-billing-smoke: flink-pipeline-package
	uv run scripts/flink_billing_smoke.py

flink-billing-recovery: flink-up
	uv run scripts/flink_billing_recovery.py

flink-observability-smoke: flink-pipeline-package
	FLINK_OBSERVABILITY_SMOKE=1 uv run scripts/flink_billing_smoke.py

flink-down:
	podman compose -f "$(FLINK_COMPOSE)" down

netty-event-loop:
	mvn -pl modules/netty/event-loop-lab -am package
	java -jar modules/netty/event-loop-lab/target/event-loop-lab.jar

netty-framing:
	mvn -pl modules/netty/framing-lab -am package
	java -jar modules/netty/framing-lab/target/framing-lab.jar

netty-backpressure:
	mvn -pl modules/netty/backpressure-lab -am package
	java -jar modules/netty/backpressure-lab/target/backpressure-lab.jar

netty-lifecycle:
	mvn -pl modules/netty/lifecycle-lab -am package
	java -jar modules/netty/lifecycle-lab/target/lifecycle-lab.jar

netty-up:
	podman compose -f "$(NETTY_COMPOSE)" up -d --build

netty-smoke:
	uv run scripts/netty_deployment_smoke.py

netty-down:
	podman compose -f "$(NETTY_COMPOSE)" down
