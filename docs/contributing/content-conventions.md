---
title: Content conventions
description: Metadata, naming, linking, and maintenance rules for Flos tutorials.
created: 2026-07-29 00:00
modified: 2026-07-29 00:00
type: documentation
status: maintained
maturity: stable
tags:
    - documentation
    - contributing
---

# Content conventions

Markdown files under `docs/` are the source of truth. Generated site output (`site/`) is never committed.

## Topic organization

Organize pages by concept and topic, not by creation date. A page should answer one coherent learning question and link to executable code where behavior matters. New topics appear in navigation automatically once the file exists in the right directory; `mkdocs-awesome-pages-plugin` reads each directory's `.pages` file for title and ordering, and appends anything not explicitly listed.

## Frontmatter

Every page defines `title`, `description`, `created`, `modified`, `type`, `status`, `maturity`, and `tags`. `scripts/validate_docs_frontmatter.py` validates these fields during `make docs-check`.

- `status` describes workflow: `inbox`, `active`, `paused`, or `maintained`.
- `maturity` describes knowledge quality: `seed`, `developing`, or `stable`.
- `tags` are the only subject-classification field.
- `source` identifies directly derived external material and is omitted for original explanations.

## Names, tags, and links

Markdown filenames and tags use lowercase kebab-case. Filenames are stable identifiers; readable capitalization and alternate names belong in `title` and `aliases`.

Internal links must be relative Markdown links ending in `.md` (for example `../state/checkpoint-recovery.md`), not extension-less clean URLs. MkDocs rewrites `.md`-suffixed links to the correct final path automatically; anything else is left as a literal string and can silently resolve to the wrong page once directory-style URLs are involved. `make docs-check` runs `mkdocs build --strict`, which reports any link it cannot resolve this way. Add links only when the target exists.

## Sources

Use the most direct durable source available. Mark quotations in the body and include a page, section, or timestamp when possible. Never use placeholder sources such as `todo`, `tbd`, or `unknown`.

## Maintenance

Run `make fmt` to format Python automation, `make check` for source and metadata checks, and `make validate` before opening a pull request.
