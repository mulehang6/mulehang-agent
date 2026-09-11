# Change And Verification Tools

Manual source edits and file creation use your own tools, following user and repository rules. Do not use `create_new_file`.

| Tool | Use it when | Key usage rule |
| --- | --- | --- |
| `rename_refactoring` | Renaming a class, method, field, variable, package, or another symbol | Use the IDE-resolved symbol target; never replace identifier text manually. Review the result before continuing. |
| `reformat_file` | Formatting needs to follow IDEA settings | Reformat only affected files; do not run broad unrelated formatting. |
| `get_file_problems` | A changed file needs error/warning inspection | Run immediately after each single-file edit and before changing another file; include warnings unless the task requires errors only. Address clear new errors before continuing. |
| `lint_files` | Several selected files need inspection | Prefer it to repeated single-file checks when the changed set is known. It is not a replacement for a project build. |
| `build_project` | Compilation/build validation is appropriate | Rebuild affected files when enough; use full rebuild only when necessary. Read returned problems rather than assuming success. |
| `recognize_ij_module_kind` | You need to identify an IntelliJ-module template kind | Specialist tool for IntelliJ plugin/module work. |
| `create_ij_module` | The task explicitly asks to create an IntelliJ module | Confirm parent directory and allowed template kind first. Do not use for ordinary source/module additions. |
| `execute_terminal_command` | A project command is needed and no structured IDEA alternative fits | Follow repository shell restrictions; do not prefer it over structured search, inspection, build, or run tools. |

## Required verification sequence

1. Make the smallest requested patch for one source file.
2. Immediately run `get_file_problems` for that just-edited file. Do not edit another file until the result is read and each new issue is fixed or explicitly recorded.
3. Repeat steps 1–2 for every additional source file. Do not wait until a batch patch or a build to read IDE problems: that loses the per-file feedback needed to isolate errors.
4. Use `lint_files` only after the per-file checks, when a known changed set needs a broader IDE inspection.
5. Use `build_project` for compilation-sensitive work, scoped to affected files if possible, as cross-file validation.
6. Report the exact check and whether it succeeded, timed out, or found remaining problems.

Use the project's own tests/build commands only when no appropriate structured IDEA build/run action exists or repository rules require the command.

## Parameter fields

`!` is schema-required and `?` is optional. Pass known `projectPath`. In the 2026.2 router, test the documented form with a minimal non-mutating call if validation reports a missing field: `get_file_problems` requires `--filePath`, inspection validation requires `--inspectionKtsCode` and `--pathToSpecification`, while `lint_files --files` accepts JSON text. Do not use an actual rename, reformat, patch, file/module creation, or run solely to discover flags.

| Tool | Fields |
| --- | --- |
| `build_project` | `filesToRebuild?`, `rebuild?`, `timeout?`, `projectPath?` |
| `execute_terminal_command` | `command!`, `executeInShell?`, `maxLinesCount?`, `reuseExistingTerminalWindow?`, `timeout?`, `truncateMode?`, `projectPath?` |
| `get_file_problems` | `filePath!`, `errorsOnly?`, `timeout?`, `projectPath?` |
| `lint_files` | `files!`, `min_severity?`, `timeout?`, `projectPath?` |
| `recognize_ij_module_kind` | `descriptorPath!`, `projectPath?` |
| `reformat_file` | `files!`, `projectPath?` |
| `rename_refactoring` | `pathInProject!`, `symbolName!`, `newName!`, `projectPath?` |
| `create_ij_module` | `moduleName!`, `parentDirectoryPath!`, `kindTemplateName!`, `projectPath?` |

In router mode, arrays/objects are JSON, for example `--files '["src/A.kt","src/B.kt"]'`. `rename_refactoring` needs the containing project-relative path plus the current symbol name and new name, not a global text match.

Router pitfall: `reformat_file` does not accept singular `--filePath`; it reports `Missing required parameters: files`. Even for one file, pass a JSON array through `--files`.
