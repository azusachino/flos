CONCEPT ?= flink
TOPIC ?=
TITLE ?=
FLINK_COMPOSE := environments/flink/compose.yaml

.PHONY: setup fmt fmt-check lint test check validate clean docs docs-check topic-new concept-check concept-test flink-event-time flink-recovery flink-savepoint-upgrade flink-recovery-package flink-package flink-pipeline-package flink-up flink-smoke flink-billing-smoke flink-observability-smoke flink-down netty-event-loop netty-framing netty-backpressure netty-lifecycle

setup:
	uv sync
	bun install --cwd docs --frozen-lockfile

fmt:
	uv run ruff format scripts
	bun run --cwd docs format

fmt-check:
	uv run ruff format --check scripts
	bun run --cwd docs format:check

lint:
	uv run ruff check scripts
	bun run --cwd docs check

test:
	mvn test

check: fmt-check lint test

validate: check docs-check flink-recovery-package flink-package flink-pipeline-package

clean:
	mvn clean
	rm -rf docs/dist

docs:
	bun run --cwd docs dev

docs-check:
	bun run --cwd docs build

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

flink-up: flink-package flink-pipeline-package
	podman compose -f "$(FLINK_COMPOSE)" up -d

flink-smoke:
	uv run scripts/flink_smoke.py

flink-billing-smoke: flink-pipeline-package
	uv run scripts/flink_billing_smoke.py

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
