#!/usr/bin/env python3

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import urlparse

import yaml

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DOCS_ROOT = PROJECT_ROOT / "docs"

TIMESTAMP_PATTERN = re.compile(r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$")
KEBAB_CASE_PATTERN = re.compile(r"^[^\W\d_]+(?:-[^\W\d_]+)*$", re.UNICODE)

TYPES = {"concept", "article", "course", "collection", "map", "documentation"}
STATUSES = {"inbox", "active", "paused", "maintained"}
MATURITIES = {"seed", "developing", "stable"}

REQUIRED_STRING_FIELDS = ("title", "description")


def find_docs() -> list[Path]:
    return sorted(DOCS_ROOT.rglob("*.md"))


def read_frontmatter(path: Path) -> dict[str, object] | None:
    text = path.read_text(encoding="utf-8")
    if not text.startswith("---\n"):
        return None
    end = text.find("\n---", 4)
    if end == -1:
        return None
    return yaml.safe_load(text[4:end]) or {}


def validate(path: Path, meta: dict[str, object]) -> list[str]:
    errors: list[str] = []

    for field in REQUIRED_STRING_FIELDS:
        value = meta.get(field)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"'{field}' must be a non-empty string")

    for field in ("created", "modified"):
        value = meta.get(field)
        if not isinstance(value, str) or not TIMESTAMP_PATTERN.match(value):
            errors.append(f"'{field}' must match 'YYYY-MM-DD HH:MM', got {value!r}")

    type_value = meta.get("type")
    if type_value not in TYPES:
        errors.append(f"'type' must be one of {sorted(TYPES)}, got {type_value!r}")

    status_value = meta.get("status")
    if status_value not in STATUSES:
        errors.append(f"'status' must be one of {sorted(STATUSES)}, got {status_value!r}")

    maturity_value = meta.get("maturity")
    if maturity_value not in MATURITIES:
        errors.append(f"'maturity' must be one of {sorted(MATURITIES)}, got {maturity_value!r}")

    tags = meta.get("tags")
    if not isinstance(tags, list) or not tags:
        errors.append("'tags' must be a non-empty array")
    else:
        for tag in tags:
            if not isinstance(tag, str) or not KEBAB_CASE_PATTERN.match(tag):
                errors.append(f"tag {tag!r} must be lowercase kebab-case")

    aliases = meta.get("aliases")
    if aliases is not None and (
        not isinstance(aliases, list) or not all(isinstance(a, str) for a in aliases)
    ):
        errors.append("'aliases', if present, must be an array of strings")

    source = meta.get("source")
    if source is not None:
        parsed = urlparse(str(source))
        if not (parsed.scheme and parsed.netloc):
            errors.append(f"'source', if present, must be a valid URL, got {source!r}")

    return errors


def main() -> int:
    failures: dict[Path, list[str]] = {}

    for path in find_docs():
        meta = read_frontmatter(path)
        if meta is None:
            failures[path] = ["missing frontmatter block"]
            continue
        errors = validate(path, meta)
        if errors:
            failures[path] = errors

    if not failures:
        print(f"validated frontmatter for {len(find_docs())} docs")
        return 0

    for path, errors in failures.items():
        relative = path.relative_to(PROJECT_ROOT)
        print(f"{relative}:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
    print(f"{len(failures)} doc(s) failed frontmatter validation", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
