# Run, Test, And Debug Tools

## Run and test

| Tool | Use it when | Key usage rule |
| --- | --- | --- |
| `get_run_configurations` | Before running an app, test, or runnable code location | This is the source of truth for exact configuration names, runnable file/line locations, and whether temporary launch overrides are supported. |
| `execute_run_configuration` | Run a known configuration or discovered runnable file/line | Pass an exact returned configuration name, or the returned file-and-line pair. Do not invent configuration names. Only pass argument/environment/working-directory overrides when the configuration says it supports them. |

For `waitForExit=true`, treat a timeout as an execution that may still be running; use returned output or full-output location rather than claiming a final result. Do not use ad hoc shell launch commands when an applicable run configuration exists.

## Debugging

Activate and follow the official `ij-debugger` skill whenever runtime evidence, breakpoints, stepping, frames, thread state, or expression evaluation is required. The following IDEA tool names are routed by that skill:

- Session lifecycle: `xdebug_get_debugger_status`, `xdebug_start_debugger_session`, `xdebug_control_session`
- Breakpoints: `xdebug_list_breakpoints`, `xdebug_set_breakpoint`, `xdebug_remove_breakpoint`, `xdebug_run_to_line`
- Runtime state: `xdebug_get_threads`, `xdebug_get_stack`, `xdebug_get_frame_values`, `xdebug_get_value_by_path`, `xdebug_evaluate_expression`, `xdebug_set_variable`

Do not reproduce an ad hoc debugger flow here. In particular, use status-derived session IDs, set logpoints before adding print statements, and let `ij-debugger` own breakpoint cleanup and pause/resume sequencing.

## Run parameter fields

`!` is schema-required and `?` is optional. Pass known `projectPath`; router calls convert direct camelCase fields to `--snake_case` and pass JSON values for `envs`.

| Tool | Fields |
| --- | --- |
| `get_run_configurations` | `filePath?`, `projectPath?` |
| `execute_run_configuration` | `configurationName?`, `filePath?`, `line?`, `envs?`, `programArguments?`, `workingDirectory?`, `waitForExit?`, `timeout?`, `projectPath?` |

`execute_run_configuration` uses exactly one target mode: `configurationName`, or `filePath` + `line`. The exact `xdebug_*` parameter contracts, target modes, logpoint setup, session IDs, and cleanup procedure belong exclusively to the official `ij-debugger` skill.
