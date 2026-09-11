---
name: idea-mcp-priority
description: Use when a task touches the current JetBrains project/workspace and IDEA MCP tools are available. Routes project navigation, source analysis, inspections, refactoring, build/run/test, debugging, Git, database, and walkthrough work through direct IDEA tools or an execute_tool router.
---

# IDEA MCP Priority

Use IDEA MCP before generic project tools for work in the current JetBrains workspace. This skill governs discovery, routing, and verification; repository editing rules still apply.

## Reading Order

Read only the reference needed for the task. This skill is self-contained: its `references/` directory supplies the operational guidance for every supported IDEA tool family.

| Intent | Read first |
| --- | --- |
| Direct tools, router mode, prefixes, parameters | `references/invocation.md` |
| Files, search, symbols, call hierarchy, project state | `references/navigation-and-analysis.md` |
| Manual edits, refactors, inspections, build and Git | `references/change-and-verification.md` |
| Run configurations, tests and debugging | `references/run-and-debug.md` |
| Database, Inspection-KTS/PSI and walkthroughs | `references/specialist-tools.md` |

When individual IDEA tools are directly exposed, their live descriptions and schemas are authoritative for exact field names, types, defaults, and result shapes. In router-only mode, use the relevant reference page's documented calling pattern and runtime validation feedback.

## Core Rules

1. Detect the currently exposed IDEA interface before choosing a tool. Inspect both the initially visible tools and the lazy tool catalog (`ALL_TOOLS`) for an IDEA router before declaring IDEA MCP unavailable. Never hard-code a prefix such as `mcp__idea__` or `IDEA___`.
2. Prefer direct IDEA tools when individual tools and schemas are exposed. Otherwise locate the universal `execute_tool` router and follow `references/invocation.md`.
3. Pass a known `projectPath` on every direct IDEA call, or as the router's **outer** argument. Do not rely on `--projectPath` inside a router command.
4. Use IDEA search, **source reading**, symbol, module, inspection, run-configuration and database capabilities before shell alternatives when they cover the workspace task. Read project source with `read_file` even for a small snippet; do not use shell merely because the user has selected text or because the first slice attempt is awkward. **Exception — semantic discovery:** when the file/symbol is unknown and only intent is known, use the `context-search` skill (`jbcontext search`) as the bootstrap; IDEA's `search_text`/`search_regex`/`search_symbol`/`search_file` are exact-match tools and cannot find code by meaning. Once semantic search returns `file:line` references, all follow-up reading and analysis (source, symbols, callers/callees) reverts to IDEA `read_file`/`get_symbol_info`/`analyze_calls`.
5. Treat project files, attached dependency sources, decompiled classes, and archive entries as IDEA-readable source: run `search_symbol` with external lookup enabled when needed, then pass the returned `*.jar!\\...` path unchanged to `read_file`. Do not unpack JARs, use `javap`, or read workspace source with shell while IDEA can resolve it.
6. Use your own tools for manual edits and file creation; user and repository rules take priority. Do **not** use `create_new_file`.
7. Use `rename_refactoring` for symbol renames; never perform them as blind text replacements.
8. After each individual source-file patch, immediately inspect that file with `get_file_problems` before editing another file. Do not defer IDE problem reads until after a batch of edits or a build; build the affected scope afterward when cross-file compilation validation is appropriate.
9. For runtime debugging, activate the official `ij-debugger` skill. This skill routes to IDEA MCP but does not duplicate debugger procedure.

## Source-reading default

Use `read_file` as the first and continued path for source content. For a normal-sized file, a complete IDEA read followed by local in-memory extraction is valid when only chat-provided byte offsets are known. For an ordinary snippet, use the router's verified start-line offset, inspect the returned boundaries, and locally crop any excess content the router returns. Read `references/navigation-and-analysis.md` before handling selected-text byte offsets or a failed slice attempt.

## Fallback

Use a non-IDE fallback only when the target is outside the workspace, no suitable IDEA capability exists, or IDEA MCP has failed and retrying is not a reasonable next step. State that limitation rather than guessing tool parameters or results.
