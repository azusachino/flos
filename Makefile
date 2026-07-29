CONCEPT ?= flink
TOPIC ?=
TITLE ?=
FLINK_COMPOSE := environments/flink/compose.yaml

.PHONY: setup fmt fmt-check lint test check validate clean docs docs-check topic-new concept-check concept-test flink-package flink-pipeline-package flink-up flink-smoke flink-down

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

validate: check docs-check flink-package flink-pipeline-package

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

flink-package:
	mvn -pl modules/flink/operator-lab -am package

flink-pipeline-package:
	mvn -pl modules/flink/pipeline-lab -am package

flink-up: flink-package flink-pipeline-package
	podman compose -f "$(FLINK_COMPOSE)" up -d

flink-smoke:
	uv run scripts/flink_smoke.py

flink-down:
	podman compose -f "$(FLINK_COMPOSE)" down
