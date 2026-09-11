# Navigation And Analysis Tools

Use these tools for read-only understanding of the active project. Always pass known `projectPath`.

| Tool | Use it when | Key usage rule |
| --- | --- | --- |
| `search_file` | You know all or part of a filename/path | Start here for file discovery; router search text is `--q`. |
| `list_directory_tree` | You need the project structure or a directory's children | Prefer it to shell directory listing. Scope to the smallest useful directory. |
| `read_file` | You need source, a target file, attached source, decompiled code, or an archive entry | Use a returned path as-is. In router mode use `--file_path`. |
| `open_file_in_editor` | The user needs the target shown in IDEA | Locate first with search when needed; use a line/offset only when known. |
| `get_all_open_file_paths` | Current editor context matters | Inspect open files before assuming which file the user means. |
| `search_text` | You need literal project content search | Scope by directory/file mask when supported instead of searching the entire repository. |
| `search_regex` | You need a pattern rather than literal text | Keep patterns focused; use returned paths for follow-up reads. |
| `search_symbol` | You only know an identifier, short name, or FQN fragment | Start here before semantic/call analysis; enable external lookup only when dependency source is relevant. |
| `get_symbol_info` | File location is known and declaration/signature/docs are needed | It is not a replacement for reading method bodies or broad control flow. |
| `analyze_calls` | You need actual callers or callees | Resolve a callable FQN with `search_symbol`; choose incoming/outgoing direction; page/expand with returned tree paths. Prefer this over text search for call relationships. |
| `get_project_modules` | Module names/types are needed | Do not infer this from Gradle/Maven files unless raw build text is the goal. |
| `get_project_dependencies` | Declared project/module dependencies matter | Use it before inspecting dependency declarations manually. |
| `get_repositories` | VCS roots or remotes matter | Use it before guessing repository layout. |
| `git_status` | You need working-tree, staged, or branch state | Read-only status first; do not discard or overwrite user changes. |
| `find_lock_requirements_usages` | The task is explicitly about lock requirements | Specialist heuristic; not a default reference search. |
| `find_threading_requirements_usages` | The task is explicitly about threading requirements | Specialist heuristic; not a default reference search. |
| `skill_search` | IDEA-side skill discovery is specifically requested | Do not substitute it for project text or symbol search. |

## Usual flows

**Find and understand a symbol:** `search_symbol` → `read_file` / `get_symbol_info` → `analyze_calls` when caller/callee facts matter.

**Explore unfamiliar code:** `list_directory_tree` → `search_file` or `search_text` → `read_file` → `get_symbol_info` for semantic details.

**Unknown intent, no keyword:** `jbcontext search` (context-search skill) as the semantic bootstrap → switch to IDEA `read_file` on the returned `file:line` → `get_symbol_info` / `analyze_calls` for follow-up analysis. IDEA search tools are exact-match only; do not guess keywords when semantic discovery already located the code.

**Inspect external code:** `search_symbol` with external lookup → `read_file` on the IDE-resolved source/decompiled path. Do not unpack JARs with shell while IDEA can resolve them.

**Read a source snippet:** `read_file` with its verified line window. If the user supplies only chat selection byte offsets, read the normal-sized source through IDEA and extract the byte range in memory; do not switch to shell source reads.

## External-source pitfalls observed in IDEA 2026.2

- `search_symbol` does not include dependencies by default. In router mode pass `--include_external true`; otherwise a library symbol may appear missing even when source is attached.
- Reuse the returned relative archive path verbatim, including the `*.jar!\\...` or `src.zip!\\...` separator. Do not normalize it to a filesystem path and do not remove `!`.
- `read_file` can read that external archive path, but `get_symbol_info` may reject the same path as outside the project. For dependency declarations and method bodies, use `read_file`; reserve `get_symbol_info` for project files unless a live call proves external support.
- Router `read_file --offset` is a 1-based line start, not a byte offset. In IDEA 2026.2, `--limit` may be silently ignored even when `--offset` takes effect; treat it as a best-effort hint, inspect the returned boundary lines, and crop any excess content in memory. Omit both to read a normal-sized source file completely.
- Chat attachments may describe a selection with UTF-8 byte offsets. Those offsets are not `read_file --offset` values. Preserve the IDEA source-read path: for a normal-sized file, read it once through IDEA and map the selected byte range in memory; with no selection, use the same `read_file` workflow with a known line window instead.
- `read_file --line_start/--line_end` and made-up byte-range flags are not supported router parameters. They can be silently ignored and return more source than requested. Use `--offset` as the verified start-line control; treat `--limit` as optional/best-effort and always verify the returned boundary lines before treating the result as a slice.

## Parameter fields

`!` is schema-required and `?` is optional. Always include known `projectPath` even though schemas mark it optional. In direct mode use the field names exactly. In the 2026.2 router, `read_file` is the notable `--file_path` exception; `list_directory_tree`, `open_file_in_editor`, symbol info, requirement searches, and call analysis instead require their camelCase names. Keep `projectPath` on the outer router call.

| Tool | Fields |
| --- | --- |
| `analyze_calls` | `analysisKind!`, `symbolFqn!`, `childOffset?`, `depth?`, `maxChildren?`, `maxNodes?`, `treePath?`, `timeout?`, `projectPath?` |
| `find_lock_requirements_usages` | `filePath!`, `line!`, `column!`, `timeout?`, `projectPath?` |
| `find_threading_requirements_usages` | `filePath!`, `line!`, `column!`, `timeout?`, `projectPath?` |
| `get_all_open_file_paths` | `projectPath?` |
| `get_project_dependencies` | `projectPath?` |
| `get_project_modules` | `projectPath?` |
| `get_repositories` | `projectPath?` |
| `get_symbol_info` | `filePath!`, `line!`, `column!`, `projectPath?` |
| `git_status` | `includeIgnored?`, `includeUntracked?`, `limit?`, `repositoryPathRelativeToProject?`, `projectPath?` |
| `list_directory_tree` | `directoryPath!`, `maxDepth?`, `timeout?`, `projectPath?` |
| `open_file_in_editor` | `filePath!`, `projectPath?` |
| `read_file` | `file_path!`, `offset?` (1-based line), `limit?` (max 5000 lines), `projectPath?` |
| `search_file` | `q!`, `paths?`, `includeExcluded?`, `limit?`, `projectPath?` |
| `search_regex` | `q!`, `paths?`, `limit?`, `projectPath?` |
| `search_symbol` | `q!`, `paths?`, `include_external?`, `limit?`, `projectPath?` |
| `search_text` | `q!`, `paths?`, `limit?`, `projectPath?` |
| `skill_search` | `q!`, `mode!`, `paths?`, `includeExcluded?`, `include_external?`, `limit?`, `projectPath?` |

Use 1-based `line`/`column` positions unless the live schema says otherwise. `analysisKind` is `INCOMING_CALLS` or `OUTGOING_CALLS`; returned `treePath` values are reused verbatim for expansion.
