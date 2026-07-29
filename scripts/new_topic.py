#!/usr/bin/env python3

from __future__ import annotations

import argparse
import re
from datetime import datetime
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CONTENT_ROOT = PROJECT_ROOT / "docs" / "src" / "content" / "docs" / "concepts"
SEGMENT_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")


def valid_segment(value: str) -> str:
    if not SEGMENT_PATTERN.fullmatch(value):
        raise argparse.ArgumentTypeError(f"{value!r} must use lowercase kebab-case")
    return value


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Create a topic-focused tutorial page")
    parser.add_argument("--concept", required=True, type=valid_segment)
    parser.add_argument("--topic", required=True)
    parser.add_argument("--title", required=True)
    return parser.parse_args()


def topic_path(concept: str, topic: str) -> Path:
    segments = topic.split("/")
    if not segments or any(not SEGMENT_PATTERN.fullmatch(segment) for segment in segments):
        raise ValueError("topic path segments must use lowercase kebab-case")
    return CONTENT_ROOT / concept / Path(*segments).with_suffix(".md")


def render(title: str, concept: str, topic: str, timestamp: str) -> str:
    topic_tag = topic.rsplit("/", maxsplit=1)[-1]
    return f"""---
title: {title}
description: A developing tutorial about {title}.
created: {timestamp}
modified: {timestamp}
type: concept
status: active
maturity: seed
tags:
  - {concept}
  - {topic_tag}
---

# {title}

## Learning objective

Explain the observable behavior of {title} and verify it with an executable example.

## Mental model

Develop the concept from input, transformation, and output semantics.

## Run the example

Use the concept-specific Make target documented by this tutorial.
"""


def main() -> int:
    args = parse_args()
    destination = topic_path(args.concept, args.topic)
    if destination.exists():
        raise SystemExit(f"Refusing to overwrite existing topic: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().astimezone().strftime("%Y-%m-%d %H:%M")
    destination.write_text(
        render(args.title, args.concept, args.topic, timestamp), encoding="utf-8"
    )
    print(destination.relative_to(PROJECT_ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
