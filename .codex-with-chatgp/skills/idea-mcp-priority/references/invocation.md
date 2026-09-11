# Invocation Contracts

## Select the current mode

Inspect the tools currently visible to the agent.

- **Direct mode:** individual IDEA functions are exposed. Locate the function by its suffix and purpose, then use its live description and JSON schema. Prefixes vary by host and are not part of the contract.
- **Router mode:** only a function whose name ends in `execute_tool` is exposed, usually described as a universal/dynamic IDE tool executor. It invokes named IDEA tools through a `command` string.

Live direct schemas win over this reference. In router mode, use the relevant topic reference for the tool's purpose and calling pattern; use runtime validation feedback only to settle a parameter spelling or required value that is specific to the current router version.

## Direct mode

Call the matching exposed tool and use its declared `camelCase` fields. Include known `projectPath`.

```ts
await directReadFile({
  filePath: "shared/src/commonMain/.../Settings.kt",
  projectPath: "D:\\work\\project",
})
```

Do not derive argument names from a tool name; copy them from the live schema or the generated reference document.

## Router mode

Call the router with two layers:

```ts
await executeTool({
  command: "read_file --file_path shared/src/commonMain/.../Settings.kt",
  projectPath: "D:\\work\\project",
})
```

- `projectPath` belongs to the router's outer argument object.
- Router flag casing is version- and tool-specific. Begin with the documented spelling, then make one minimal validation call if it is rejected. In the 2026.2 router, `read_file --file_path` and search `--q` use snake case, while many navigation, inspection, and build tools require camel case such as `--filePath`, `--directoryPath`, `--analysisKind`, `--symbolFqn`, `--descriptorPath`, `--inspectionKtsCode`, and `--pathToSpecification`. A silently ignored flag commonly appears as a missing camel-case required parameter.
- Do not pass a nested `{ tool_name, arguments }` object to the router. Its outer contract is `command` plus `projectPath`; put the invoked IDEA tool and its flags entirely in `command`.
- Quote paths or values containing spaces. Pass objects and arrays as JSON when the router requests them.
- An unknown tool invocation can reveal the current router's tool-name list. It does **not** reliably reveal descriptions or full schemas.
- Do not use `<tool> --help` as a contract source: some versions parse it as an ordinary parameter or ignore it.

## Contract resolution

1. Prefer the live direct schema.
2. In router mode, read the relevant topic reference from this skill and follow its documented tool flow.
3. For router-only parameter spelling, use validation feedback for the minimum needed call; do not invent a full schema.
4. If the router cannot resolve the project, verify that `projectPath` is outermost before asking the user to choose from the returned open-project list.
5. Treat any project-local generated tool catalog as supplementary and potentially stale; never require it to apply this skill.

## Router observations (IDEA 2026.2)

- The result is a `CallToolResult`; operational success or failure is often encoded in `content[0].text` and `isError`, not thrown as a tool exception. Some text is JSON encoded a second time (notably `git_status`), so parse it before drawing conclusions.
- An unknown tool name returns the available tool-name list, but not reliable descriptions or parameter schemas. `<tool> --help` likewise cannot establish a contract.
- JSON-valued parameters must remain JSON text: `search_text` and `search_regex` require `--paths '["src"]'`, not a scalar path. `skill_search --mode` accepts `file`, `text`, `regex`, or `symbol`; `all` is rejected.
- `open_file_in_editor` is not data-mutating, but it changes the editor focus; do not use it as a strictly no-UI-side-effect probe.
- Treat `build_project` as failed when `isSuccess: false` or `timedOut: true`, even if the outer call has no `isError`. The current project timed out after reporting Kotlin `expect`/`actual` Beta warnings.
- `execute_terminal_command` could not launch PowerShell in this environment: snake-case `--execute_in_shell` was ignored, while camel-case `--executeInShell true` failed on the quoted executable path. Prefer a structured IDEA tool or the approved host shell until this is fixed.
- `execute_terminal_command` also tried to launch a relative Windows batch path such as `.\\gradlew.bat` directly and failed with `CreateProcess error=2`; it did not automatically invoke `cmd.exe` or PowerShell. After one such failure, use `build_project`, a matching run configuration, or the approved host PowerShell instead of repeatedly changing quoting.
