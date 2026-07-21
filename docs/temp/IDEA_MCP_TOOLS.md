# IDEA MCP 工具参考

> 生成日期：2026-07-17
>
> 本文依据当前 Codex 会话暴露的 IDEA MCP 元数据自动整理，共 66 个工具。每个工具条目保留其原始完整描述，其中的 TypeScript 声明是权威参数、类型、默认值和返回值说明。IDEA 或 MCP 插件更新后，应重新生成本文以避免文档过期。

## 路由模式与调用建议

截图中的 IDEA 设置说明表明，勾选“仅路由器”后，工具不会直接出现在 MCP 工具列表中，而应通过专用的通用工具访问。日常使用建议重新开启该模式，以减少上下文占用；需要完整工具目录、排障或更新本文时，再临时关闭它。

当前工具集中，通用入口是 `mcp__idea__execute_tool`。路由模式开启后，优先通过它按工具名和参数调用。通用调用形态以该工具自身的 schema 为准；不要猜测被路由工具的参数，直接参照本文对应条目的 TypeScript 声明。

```ts
// 示例为概念性写法：execute_tool 的实际字段名、可用工具名与参数封装
// 以本文件中 execute_tool 的原始 schema 为准。
await tools.mcp__idea__execute_tool({
  // toolName: "get_file_problems",
  // arguments: {
  //   projectPath: "D:\\JetBrains\\projects\\idea_projects\\mulehang-agent",
  //   filePath: "desktopApp/src/jvmMain/kotlin/.../ChatScreen.kt",
  // },
});
```

无论直接调用还是经路由器调用，只要已知项目路径，都应传入 `projectPath`：

```text
D:\JetBrains\projects\idea_projects\mulehang-agent
```

## 目录

- [`analyze_calls`](#analyze-calls)
- [`apply_patch`](#apply-patch)
- [`await_walkthrough_question`](#await-walkthrough-question)
- [`build_project`](#build-project)
- [`cancel_sql_query`](#cancel-sql-query)
- [`create_database_connection`](#create-database-connection)
- [`create_ij_module`](#create-ij-module)
- [`create_new_file`](#create-new-file)
- [`edit_database_connection`](#edit-database-connection)
- [`execute_run_configuration`](#execute-run-configuration)
- [`execute_sql_query`](#execute-sql-query)
- [`execute_terminal_command`](#execute-terminal-command)
- [`execute_tool`](#execute-tool)
- [`fetch_query_result`](#fetch-query-result)
- [`find_lock_requirements_usages`](#find-lock-requirements-usages)
- [`find_threading_requirements_usages`](#find-threading-requirements-usages)
- [`generate_inspection_kts_api`](#generate-inspection-kts-api)
- [`generate_inspection_kts_examples`](#generate-inspection-kts-examples)
- [`generate_psi_tree`](#generate-psi-tree)
- [`get_all_open_file_paths`](#get-all-open-file-paths)
- [`get_database_object_description`](#get-database-object-description)
- [`get_file_problems`](#get-file-problems)
- [`get_project_dependencies`](#get-project-dependencies)
- [`get_project_modules`](#get-project-modules)
- [`get_repositories`](#get-repositories)
- [`get_run_configurations`](#get-run-configurations)
- [`get_symbol_info`](#get-symbol-info)
- [`git_status`](#git-status)
- [`insert_walkthrough_tangents`](#insert-walkthrough-tangents)
- [`introspect_schema`](#introspect-schema)
- [`lint_files`](#lint-files)
- [`list_database_connections`](#list-database-connections)
- [`list_database_schemas`](#list-database-schemas)
- [`list_directory_tree`](#list-directory-tree)
- [`list_recent_sql_queries`](#list-recent-sql-queries)
- [`list_schema_object_kinds`](#list-schema-object-kinds)
- [`list_schema_objects`](#list-schema-objects)
- [`open_file_in_editor`](#open-file-in-editor)
- [`preview_table_data`](#preview-table-data)
- [`read_file`](#read-file)
- [`recognize_ij_module_kind`](#recognize-ij-module-kind)
- [`reformat_file`](#reformat-file)
- [`rename_refactoring`](#rename-refactoring)
- [`run_inspection_kts`](#run-inspection-kts)
- [`search_file`](#search-file)
- [`search_regex`](#search-regex)
- [`search_symbol`](#search-symbol)
- [`search_text`](#search-text)
- [`show_diff_walkthrough_items`](#show-diff-walkthrough-items)
- [`show_walkthrough_items`](#show-walkthrough-items)
- [`skill_search`](#skill-search)
- [`test_database_connection`](#test-database-connection)
- [`validate_inspection_kts`](#validate-inspection-kts)
- [`xdebug_control_session`](#xdebug-control-session)
- [`xdebug_evaluate_expression`](#xdebug-evaluate-expression)
- [`xdebug_get_debugger_status`](#xdebug-get-debugger-status)
- [`xdebug_get_frame_values`](#xdebug-get-frame-values)
- [`xdebug_get_stack`](#xdebug-get-stack)
- [`xdebug_get_threads`](#xdebug-get-threads)
- [`xdebug_get_value_by_path`](#xdebug-get-value-by-path)
- [`xdebug_list_breakpoints`](#xdebug-list-breakpoints)
- [`xdebug_remove_breakpoint`](#xdebug-remove-breakpoint)
- [`xdebug_run_to_line`](#xdebug-run-to-line)
- [`xdebug_set_breakpoint`](#xdebug-set-breakpoint)
- [`xdebug_set_variable`](#xdebug-set-variable)
- [`xdebug_start_debugger_session`](#xdebug-start-debugger-session)

## 完整工具说明

### `analyze_calls`

Builds the IDE Call Hierarchy tree for a method, function, constructor, or supported type target.
Use it to see who calls a symbol (`INCOMING_CALLS`) or what the symbol calls (`OUTGOING_CALLS`).
Strongly prefer this tool over usage search, text search, or regex search when evaluating dependencies by actual calls.
It uses IDE call hierarchy data, so it provides more precise call relationships with less noise and fewer follow-up calls than primitive searches.

Pass `symbolFqn` as a fully qualified name, for example `com.example.Service.run`.
If the name is ambiguous, the tool returns exact signatures; pass one of them back as `symbolFqn`.
If you only know a short name or fragment, use `search_symbol` first to find the target.

The result is an expandable text tree. Each node includes `filePath` and `treePath`; `filePath` is project-relative when possible.
Pass `treePath` back to render the subtree.
Use `childOffset` to continue after a truncated `… and n more` line.
`depth`, `maxChildren`, and `maxNodes` bound the rendered tree.
Symbols can come from project sources, source jars, or decompiled binary jar dependencies when the IDE can resolve them.

exec tool declaration:
```ts
declare const tools: { mcp__idea__analyze_calls(args: {
  // Call analysis direction. Use `INCOMING_CALLS` to show callers of `symbolFqn`, or `OUTGOING_CALLS` to show symbols called from `symbolFqn`.
  analysisKind: "INCOMING_CALLS" | "OUTGOING_CALLS";
  // Offset for paging direct children of the node addressed by `treePath`. Default: 0.
  childOffset?: number;
  // Maximum number of call levels to render below the requested subtree root. Default: 5. Use 0 to render only the subtree root.
  depth?: number;
  // Maximum number of direct children rendered for each node. Default: 50.
  maxChildren?: number;
  // Maximum total number of rendered call nodes. Default: 1000.
  maxNodes?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Plain fully qualified symbol name, or an exact signature returned by an ambiguity error or copied from a rendered child node. If you only know a short name or fragment, use `search_symbol` first and pass the best fully qualified callable name here. Examples: `com.example.Service.run`, `com.example.Service.run(String)`, or `org.assertj.core.api.Assertions.assertThat(String)`. Do not pass file path, line, column, or a separate target signature.
  symbolFqn: string;
  // Timeout in milliseconds
  timeout?: number;
  // Optional path to a subtree root, copied exactly from a previous `analyze_calls` result. Null or omitted means the root path `[]`. Each component is an exact signature, not a display name.
  treePath?: Array<string>;
}): Promise<CallToolResult>; };
```

### `apply_patch`

Apply a patch using the Codex apply_patch format or unified git diff format.
        Supports Add, Delete, and Update operations with optional Move to path for updates.
        Paths must stay inside the project directory.

exec tool declaration:
```ts
declare const tools: { mcp__idea__apply_patch(args: {
  // Patch text in the apply_patch format or unified git diff format.
  input?: string;
  // Alias of `input` for compatibility with clients that send `{patch: ...}`.
  patch?: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult>; };
```

### `await_walkthrough_question`

Suspends until the user types a follow-up question into the active walkthrough popup and presses Send, then returns the question text along with the label of the step the user was viewing. Returns 'dismissed' if the user closes the popup before asking. Returns 'waiting-expired' before Codex's tool timeout if no question arrives; when that happens, immediately call this tool again with the same walkthroughId. Call this immediately after show_walkthrough_items or show_diff_walkthrough_items returns, and call it again after each insert_walkthrough_tangents response. Keep waiting in this loop until this tool returns dismissed. Call insert_walkthrough_tangents to splice each answer into the walkthrough as labeled child steps.

exec tool declaration:
```ts
declare const tools: { mcp__idea__await_walkthrough_question(args: {
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // The walkthroughId returned by show_walkthrough_items.
  walkthroughId: string;
}): Promise<CallToolResult>; };
```

### `build_project`

Triggers building of the project or specified files, waits for completion, and returns build errors.
Use this tool to build the project or compile files and get detailed information about compilation errors and warnings.
You have to use this tool after performing edits to validate if the edits are valid.

exec tool declaration:
```ts
declare const tools: { mcp__idea__build_project(args: {
  // If specified, only compile files with the specified paths. Paths are relative to the project root.
  filesToRebuild?: Array<string>;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Whether to perform full rebuild the project. Defaults to false. Effective only when `filesToRebuild` is not specified.
  rebuild?: boolean;
  // Timeout in milliseconds
  timeout?: number;
}): Promise<CallToolResult<{
  // Whether the build was successful
  isSuccess?: boolean | null;
  // A list of problems encountered during the build. May be empty if the build was successful.
  problems: Array<{ column?: number | null; description?: string | null; file?: string | null; group?: string | null; kind?: string | null; line?: number | null; message: string; }>;
  // Indicates whether the operation was timed out. 'true' value may mean that the results may be incomplete or partial. 'false', 'null' or missing value means that the operation has not been timed out.
  timedOut?: boolean | null;
}>>; };
```

### `cancel_sql_query`

Cancels a running query using its unique ID. 

exec tool declaration:
```ts
declare const tools: { mcp__idea__cancel_sql_query(args: {
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // The unique ID of a query session.
  sessionId: number;
}): Promise<CallToolResult>; };
```

### `create_database_connection`

Create a new database connection (data source) by name, DBMS (dbms), JDBC URL (url) and flag to check connection (needToCheckDs). All parameters are required.
Returns connection diagnostic info.

exec tool declaration:
```ts
declare const tools: { mcp__idea__create_database_connection(args: {
  // Name of the database management system (DBMS)
  dbms: string;
  // Unique name of the database connection.
  name: string;
  // Whether to test the connection right after the data source is created/edited.
  // Set to `false` when configuring multiple connections in a batch — the per-connection probe is expensive,
  // and `test_database_connection` can be called explicitly for the connections that matter.
  needToCheckDs: boolean;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Fully-formed JDBC URL of the database connection (e.g. `jdbc:postgresql://<host>:<port>/<database>`)
  url: string;
}): Promise<CallToolResult<{
  // Detailed information about database connection such as DBMS type, version and JDBC driver.
  connectionInfo: string;
  // Shows if the connection is problematic (yes/no/unknown).
  hasProblems: "YES" | "NO" | "UNSURE";
  // Summary of connection attempt result. In case of a failure, contains DBMS-provided error description.
  summary: string;
}>>; };
```

### `create_ij_module`

Creates a new IntelliJ module using the same scaffolding logic as the New IntelliJ Module action,
but without showing any UI or progress indicators.
For non-empty kinds, this also updates the enclosing plugin.xml when a single target plugin can be resolved.
Allowed values for kindTemplateName: `empty`, `frontend`, `backend`, `shared`.
Note: parentDirectoryPath must point to an existing directory inside the project.

exec tool declaration:
```ts
declare const tools: { mcp__idea__create_ij_module(args: {
  // Module kind template name. Allowed values: `empty`, `frontend`, `backend`, `shared`.
  kindTemplateName: string;
  // Module name to create. For non-empty kinds the same name normalization as in the UI action is applied.
  moduleName: string;
  // Path relative to the project root
  parentDirectoryPath: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{
  // Normalized module kind template name.
  kindTemplateName: string;
  // Created module name after the same kind-based normalization used by the UI action.
  moduleName: string;
  // Path to the created module root, relative to the project root when possible.
  moduleRootPath: string;
  // Path to the plugin.xml selected for content-module registration, relative to the project root when available.
  targetPluginXmlPath?: string | null;
}>>; };
```

### `create_new_file`

Creates a new file at the specified path within the project directory and optionally populates it with text if provided.
Use this tool to generate new files in your project structure.
Note: Creates any necessary parent directories automatically

exec tool declaration:
```ts
declare const tools: { mcp__idea__create_new_file(args: {
  // Whether to overwrite an existing file if exists. If false, an exception is thrown in case of a conflict.
  overwrite?: boolean;
  // Path where the file should be created relative to the project root
  pathInProject: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Content to write into the new file
  text?: string;
}): Promise<CallToolResult>; };
```

### `edit_database_connection`

Edit an existing database connection (data source) identified by `connectionId`.
Updates the connection's DBMS driver and JDBC URL; the connection `name` is preserved.
Required parameters: connectionId, dbms, url, needToCheckDs
Returns connection diagnostic info.
Do not use this tool for DDL data sources as they have no underlying DBMS connection.

exec tool declaration:
```ts
declare const tools: { mcp__idea__edit_database_connection(args: {
  // The unique ID of a database connection.
  connectionId: string;
  // Name of the database management system (DBMS)
  dbms: string;
  // Whether to test the connection right after the data source is created/edited.
  // Set to `false` when configuring multiple connections in a batch — the per-connection probe is expensive,
  // and `test_database_connection` can be called explicitly for the connections that matter.
  needToCheckDs: boolean;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Fully-formed JDBC URL of the database connection (e.g. `jdbc:postgresql://<host>:<port>/<database>`)
  url: string;
}): Promise<CallToolResult<{
  // Detailed information about database connection such as DBMS type, version and JDBC driver.
  connectionInfo: string;
  // Shows if the connection is problematic (yes/no/unknown).
  hasProblems: "YES" | "NO" | "UNSURE";
  // Summary of connection attempt result. In case of a failure, contains DBMS-provided error description.
  summary: string;
}>>; };
```

### `execute_run_configuration`

Run either an existing run configuration by name or a temporary run configuration created from a code location (`filePath` + `line`) in the current project, then wait up to specified timeout for it to finish.

Use this tool with either a configuration name returned by `get_run_configurations`, or with a run point
(`filePath` + `line`) returned by `get_run_configurations(filePath = ...)`.

Optional launch overrides (`programArguments`, `workingDirectory`, `envs`) are applied only for this run and are not persisted.
Do not pass these override parameters unless you explicitly need to change the configured launch values for this run.
Missing/null override parameters keep existing run configuration values unchanged.
For string overrides (`programArguments`, `workingDirectory`), missing/null or empty string (`""`) keeps the existing value unchanged.
Pass a whitespace-only string such as `" "` to clear an existing value for this launch.

Pass either `configurationName`, or `filePath` together with `line`. These modes are mutually exclusive.

Behavior:
- When `waitForExit=true`, waits up to `timeout` milliseconds for process termination. If the timeout expires,
  the process keeps running in the background and `exitCode` is omitted from the result.
- When `waitForExit=false`, waits only for the process to start, then returns immediately without applying `timeout`.
- `fullOutputPath` points to a temp file with the full raw output and may continue growing while the process is alive.

Returns the execution result including current output snapshot, optional exit code, and optional `fullOutputPath`.

exec tool declaration:
```ts
declare const tools: { mcp__idea__execute_run_configuration(args: {
  // Name of the existing run configuration to execute
  configurationName?: string;
  // Optional environment variable overrides for this launch only. Missing/null keeps existing env unchanged; when provided, values are merged over existing env.
  envs?: { [key: string]: string; };
  // File path relative to the project root. Provide together with `line` to create and execute a temporary run configuration from code context.
  filePath?: string;
  // 1-based line number for `filePath`. Provide together with `filePath` and do not combine with `configurationName`.
  line?: number;
  // Optional program arguments override for this launch only. Missing/null or empty string keeps the existing value; whitespace-only string clears it.
  programArguments?: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Timeout in milliseconds
  timeout?: number;
  // Whether to wait for process termination. If false, the tool returns immediately after the process starts and ignores `timeout`.
  waitForExit?: boolean;
  // Optional working directory override for this launch only. Missing/null or empty string keeps the existing value; whitespace-only string clears it.
  workingDirectory?: string;
}): Promise<CallToolResult<{
  // Process exit code. Absent when the tool returns before observing process termination, for example when `waitForExit=false` or when `timeout` expires.
  exitCode?: number | null;
  // Path to a temp file containing the full raw output. The file may continue growing while the process is still running and remains available while the IDE is running.
  fullOutputPath?: string | null;
  // Captured process output snapshot. The snapshot includes up to the first 10000 characters of process output; when additional output exists, `<truncated>` is appended to the preview.
  output: string;
  // Session identifier for this run. Uses the session name by default; if duplicate live run sessions exist, format is `<sessionName>#<executionId>`.
  sessionId?: string | null;
  // Deprecated
  timedOut?: boolean | null;
}>>; };
```

### `execute_sql_query`

Executes a SQL query against the given database connection.
Do not use this tool for DDL data sources as they have no underlying DBMS connection.
Reports execution status (success/error) with error details when applicable.
Returns query results in CSV format, if any.

exec tool declaration:
```ts
declare const tools: { mcp__idea__execute_sql_query(args: {
  // The unique ID of a database connection.
  connectionId: string;
  // Name of the database the schema belongs to. Might be empty if DBMS has no databases, but only schemas.
  // Together with `schemaName` property forms qualified schema name that uniquely identifies the schema within the connection.
  databaseName: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // SQL query to be executed.
  queryText: string;
  // Name of the schema.
  schemaName: string;
}): Promise<CallToolResult<{
  // Error description if the query failed at the database; empty otherwise. Treat any non-empty value here as a failure regardless of `text`.
  errorMessage?: string | null;
  // ID of the cached query result set. Pass it to `fetch_query_result` to retrieve additional pages. Empty if the error happens before getting any result.
  resultSetId: string;
  // Rendered query result in CSV format, or a status message if the query returned no rows. Empty if the query failed.
  text?: string | null;
}>>; };
```

### `execute_terminal_command`

Executes a specified shell command in the IDE's integrated terminal.
        Use this tool to run terminal commands within the IDE environment.
        Requires a command parameter containing the shell command to execute.
        Important features and limitations:
        - Checks if process is running before collecting output
        - Limits output to 2000 lines (truncates excess)
        - Times out after specified timeout with notification
        - Requires user confirmation unless "Brave Mode" is enabled in settings
        Returns possible responses:
        - Terminal output (truncated if > 2000 lines)
        - Output with interruption notice if timed out
        - Error messages for various failure cases

exec tool declaration:
```ts
declare const tools: { mcp__idea__execute_terminal_command(args: {
  // Shell command to execute
  command: string;
  // Whether to execute the command in a default user's shell (bash, zsh, etc.).
  // Useful if the command is not a commandline but a shell script, or if it's important to preserve real environment of the user's terminal.
  // In the case of 'false' value the command will be started as a process
  executeInShell?: boolean;
  // Maximum number of lines to return
  maxLinesCount?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Whether to reuse an existing terminal window. Allows to avoid creating multiple terminals
  reuseExistingTerminalWindow?: boolean;
  // Timeout in milliseconds
  timeout?: number;
  // How to truncate the text: from the start, in the middle, at the end, or don't truncate at all
  truncateMode?: "START" | "MIDDLE" | "END" | "NONE";
}): Promise<CallToolResult<{
  command_exit_code?: number | null;
  command_output: string;
  // Indicates whether the operation was timed out. 'true' value may mean that the results may be incomplete or partial. 'false', 'null' or missing value means that the operation has not been timed out.
  is_timed_out?: boolean | null;
}>>; };
```

### `execute_tool`

Universal tool executor that can invoke specific IDE MCP tool dynamically.

exec tool declaration:
```ts
declare const tools: { mcp__idea__execute_tool(args: {
  // Command-line string with tool name and arguments
  command: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult>; };
```

### `fetch_query_result`

Fetches rows from an already executed query by its id, starting at the given row offset.
Returns the same shape as `execute_sql_query`: the `resultSetId` and the rendered result in CSV format.
Use this tool to paginate over a previously returned `resultSetId` from `execute_sql_query` or `preview_table_data`.

exec tool declaration:
```ts
declare const tools: { mcp__idea__fetch_query_result(args: {
  // Row offset to start fetching from (0-based).
  offset: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // The opaque result set ID returned by a previous `execute_sql_query` or `preview_table_data` call.
  resultSetId: string;
}): Promise<CallToolResult<{
  // Error description if the query failed at the database; empty otherwise. Treat any non-empty value here as a failure regardless of `text`.
  errorMessage?: string | null;
  // ID of the cached query result set. Pass it to `fetch_query_result` to retrieve additional pages. Empty if the error happens before getting any result.
  resultSetId: string;
  // Rendered query result in CSV format, or a status message if the query returned no rows. Empty if the query failed.
  text?: string | null;
}>>; };
```

### `find_lock_requirements_usages`

Analyzes the usage of the Read/Write lock for the method under the caret.
Also analyzes call paths to some depth.
Use this tool to identify possible usages of Read/Write lock requirements.
Returns a list of lock requirements with the call path to them.
Important: the information is neither complete nor reliable: this is merely a heuristic. Each returned call path may not be reachable,
and there could be undetected call paths.
Note: Only analyzes files within the project directory.
Note: Lines and Columns are 1-based.

exec tool declaration:
```ts
declare const tools: { mcp__idea__find_lock_requirements_usages(args: {
  // Column where cursor is located
  column: number;
  // Path relative to the project root
  filePath: string;
  // Line where cursor is located
  line: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Timeout in milliseconds
  timeout?: number;
}): Promise<CallToolResult<{ foundRequirements: Array<{ callPath: Array<{ className: string; methodName: string; }>; type: "READ_ASSERTION" | "WRITE_ASSERTION" | "WRITE_INTENT_ASSERTION" | "NO_READ_ASSERTION"; }>; timedOut: boolean; }>>; };
```

### `find_threading_requirements_usages`

Analyzes the usage of threading constraints (i.e., whether the method needs to run on the UI thread or on the background thread) for the method under the caret.
Also analyzes call paths to some depth.
Use this tool to identify possible usages of threading requirements.
Returns a list of threading requirements with the call path to them.
Important: the information is neither complete nor reliable: this is merely a heuristic. Each returned call path may not be reachable,
and there could be undetected call paths.
Note: Only analyzes files within the project directory.
Note: Lines and Columns are 1-based.

exec tool declaration:
```ts
declare const tools: { mcp__idea__find_threading_requirements_usages(args: {
  // Column where cursor is located
  column: number;
  // Path relative to the project root
  filePath: string;
  // Line where cursor is located
  line: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Timeout in milliseconds
  timeout?: number;
}): Promise<CallToolResult<{ foundRequirements: Array<{ callPath: Array<{ className: string; methodName: string; }>; type: "UI_THREAD" | "BACKGROUND_THREAD"; }>; timedOut: boolean; }>>; };
```

### `generate_inspection_kts_api`

Returns the Inspection KTS API documentation for the target language.
    Provides available classes and functions that can be used when writing inspection.kts files.

exec tool declaration:
```ts
declare const tools: { mcp__idea__generate_inspection_kts_api(args: {
  // Target language: 'Java' or 'Kotlin'
  language: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // If true, wraps the API content in <API> and <api.kt> tags
  wrapInTags?: boolean;
}): Promise<CallToolResult>; };
```

### `generate_inspection_kts_examples`

Returns example inspection.kts templates for the target language to guide code generation.
    Provides XML-wrapped examples showing how to write inspections using the InspectionKts API.

exec tool declaration:
```ts
declare const tools: { mcp__idea__generate_inspection_kts_examples(args: {
  // If true, includes additional curated examples besides templates
  includeAdditionalExamples?: boolean;
  // Target language for examples: 'Java', 'Kotlin', or 'Any' (default)
  language?: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult>; };
```

### `generate_psi_tree`

Creates a PSI tree for provided Java or Kotlin code and returns it as indented text.
    Use this tool to understand the PSI structure of code snippets when writing inspections.
    The output shows element types and their hierarchy, with hints about when node.children() is needed.

exec tool declaration:
```ts
declare const tools: { mcp__idea__generate_psi_tree(args: {
  // Source code snippet to parse
  code: string;
  // Programming language: 'Java' or 'Kotlin'
  language: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult>; };
```

### `get_all_open_file_paths`

Returns active editor's and other open editors' file paths relative to the project root.

Use this tool to explore current open editors.

exec tool declaration:
```ts
declare const tools: { mcp__idea__get_all_open_file_paths(args: {
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ activeFilePath?: string | null; openFiles: Array<string>; }>>; };
```

### `get_database_object_description`

Retrieves the structure of a database object (columns, types, keys, indexes) within a particular schema as a hierarchical text representation.
In case of ambiguity returns definition of all applicable objects.

exec tool declaration:
```ts
declare const tools: { mcp__idea__get_database_object_description(args: {
  // The unique ID of a database connection.
  connectionId: string;
  // Name of the database the schema belongs to. Might be empty if DBMS has no databases, but only schemas.
  // Together with `schemaName` property forms qualified schema name that uniquely identifies the schema within the connection.
  databaseName: string;
  // Non-empty object kind (e.g., table, view, routine).
  kind: string;
  // Non-empty object name of the specified kind (e.g., table or view name).
  objectName: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Name of the schema.
  schemaName: string;
}): Promise<CallToolResult<{
  // DDL tree descriptions of the schema objects.
  definitions: Array<string>;
  // Schema information.
  schemaInfo: {
  // Name of the database the schema belongs to. Might be empty if DBMS has no databases, but only schemas.
  // Together with `schemaName` property forms qualified schema name that uniquely identifies the schema within the connection.
  databaseName: string;
  // Whether the schema metadata has been loaded (introspected).
  isIntrospected: boolean;
  // Name of the schema.
  schemaName: string;
};
}>>; };
```

### `get_file_problems`

Analyzes the specified file for errors and warnings using IntelliJ's inspections.
Use this tool to identify coding issues, syntax errors, and other problems in a specific file.
Returns a list of problems found in the file, including severity, description, and location information.
Note: Only analyzes files within the project directory.
Note: Lines and Columns are 1-based.

exec tool declaration:
```ts
declare const tools: { mcp__idea__get_file_problems(args: {
  // Whether to include only errors or include both errors and warnings
  errorsOnly?: boolean;
  // Path relative to the project root
  filePath: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Timeout in milliseconds
  timeout?: number;
}): Promise<CallToolResult<{
  errors: Array<{ column: number; description: string; line: number; lineContent: string; severity: string; }>;
  filePath: string;
  // Indicates whether the operation was timed out. 'true' value may mean that the results may be incomplete or partial. 'false', 'null' or missing value means that the operation has not been timed out.
  timedOut?: boolean | null;
}>>; };
```

### `get_project_dependencies`

Get a list of all dependencies defined in the project.
Includes JPS module libraries and ecosystem-specific dependencies contributed by language plugins.
(e.g. package.json, deno.json dependencies)
Each entry has a name and, when known, a version, a dependencyType (e.g. devDependencies),
and a source (e.g. jps-library, package.json).
Returns structured information about project library names.

exec tool declaration:
```ts
declare const tools: { mcp__idea__get_project_dependencies(args: {
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ dependencies: Array<{ dependencyType?: string | null; name: string; source?: string | null; version?: string | null; }>; }>>; };
```

### `get_project_modules`

Get a list of all modules in the project with their types.
Returns structured information about each module including name and type.

exec tool declaration:
```ts
declare const tools: { mcp__idea__get_project_modules(args: {
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ modules: Array<{ name: string; type?: string | null; }>; }>>; };
```

### `get_repositories`

Retrieves the list of VCS roots in the project.
This is useful to detect all repositories in a multi-repository project.

exec tool declaration:
```ts
declare const tools: { mcp__idea__get_repositories(args: {
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ roots: Array<{
  // Path of repository relative to the project directory. Empty string means the project root
  pathRelativeToProject: string;
  // VCS used by this repository
  vcsName: string;
}>; }>>; };
```

### `get_run_configurations`

Returns either project run configurations or executable code locations, depending on the input.

Without `filePath`, this tool lists the project's existing run configurations. The result includes configuration
names and, when available, launch details such as program arguments, working directory, environment variables,
and `supportsDynamicLaunchOverrides`.

`supportsDynamicLaunchOverrides` is the source-of-truth capability flag for one-time launch overrides
(`programArguments`, `workingDirectory`, `envs`) in `execute_run_configuration` and `xdebug_start_debugger_session`.
Only pass those override parameters when this flag is `true` for the selected configuration.

With `filePath`, this tool discovers executable entry points (run points) in that file, such as test methods,
main methods, or other executable entry points where the IDE shows a Run gutter icon. The result contains `filePath` and
`runPoints`; use the returned line numbers with `execute_run_configuration` to run from code.

exec tool declaration:
```ts
declare const tools: { mcp__idea__get_run_configurations(args: {
  // Optional file path relative to the project root. When provided, returns run points (executable entry points) in the file instead of project-wide run configurations.
  filePath?: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{
  // Project run configurations. Present when the tool is called without `filePath`.
  configurations?: Array<{
  // Configured command line or program arguments for this run configuration, when available.
  commandLine?: string | null;
  // Human-readable run configuration type or description shown by the IDE, when available.
  description?: string | null;
  // Configured environment variables for this run configuration, when available.
  environment?: { [key: string]: string; } | null;
  // Run configuration name. Pass this value as `configurationName` to `execute_run_configuration`.
  name: string;
  // Whether this run configuration supports one-time dynamic launch overrides for `programArguments`, `workingDirectory`, and `envs`. Use this field as the source of truth before passing those override parameters to `execute_run_configuration` or `xdebug_start_debugger_session`.
  supportsDynamicLaunchOverrides: boolean;
  // Configured working directory for this run configuration, when available.
  workingDirectory?: string | null;
}> | null;
  // File path relative to the project root for which `runPoints` were collected. Present only when the tool is called with `filePath`.
  filePath?: string | null;
  // Executable entry points discovered in `filePath`, such as test methods, main methods, or other executable entry points. Present only when the tool is called with `filePath`.
  runPoints?: Array<{
  // IDE-provided description or tooltip for this run point, when available.
  description?: string | null;
  // Short source snippet for the PSI element associated with this run point, when available.
  elementText?: string | null;
  // 1-based line number of the executable code location.
  line: number;
}> | null;
}>>; };
```

### `get_symbol_info`

Retrieves information about the symbol at the specified position in the specified file.
Provides the same information as Quick Documentation feature of IntelliJ IDEA does.

This tool is useful for getting information about the symbol at the specified position in the specified file.
The information may include the symbol's name, signature, type, documentation, etc. It depends on a particular language.

If the position has a reference to a symbol the tool will return a piece of code with the declaration of the symbol if possible.

Use this tool to understand symbols declaration, semantics, where it's declared, etc.

exec tool declaration:
```ts
declare const tools: { mcp__idea__get_symbol_info(args: {
  // 1-based column number
  column: number;
  // Path relative to the project root
  filePath: string;
  // 1-based line number
  line: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ documentation: string; partialResultReason?: string | null; symbolInfo?: { declarationFile?: string | null; declarationLine?: number | null; declarationText: string; language?: string | null; name?: string | null; } | null; }>>; };
```

### `git_status`

Retrieves Git status for one or more repositories in the current project.
Returns porcelain-style index/worktree status codes and summary counters.
By default all Git repositories are returned.

exec tool declaration:
```ts
declare const tools: { mcp__idea__git_status(args: {
  // Whether to include ignored files
  includeIgnored?: boolean;
  // Whether to include untracked files
  includeUntracked?: boolean;
  // Maximum number of entries returned per repository
  limit?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Optional path relative to project root used to select a single containing repository
  repositoryPathRelativeToProject?: string;
}): Promise<CallToolResult<{ repositories: Array<{
  // Number of conflicted entries
  conflictedCount: number;
  // Current branch name, or null when detached
  currentBranch?: string | null;
  entries: Array<{
  // Index status code from git status porcelain output
  indexStatus: string;
  // Original path for renames/copies, relative to repository root
  originalPathRelativeToRepository?: string | null;
  // Path relative to repository root
  pathRelativeToRepository: string;
  // Working tree status code from git status porcelain output
  workTreeStatus: string;
}>;
  // True when entries were truncated by 'limit'
  hasMoreEntries: boolean;
  // Number of ignored entries
  ignoredCount: number;
  // True when repository has no status entries matching the selected filters
  isClean: boolean;
  // Path of this repository relative to project root. Empty string means the project root
  repositoryPathRelativeToProject: string;
  // Number of entries with staged changes
  stagedCount: number;
  // Total number of status entries before applying 'limit'
  totalEntries: number;
  // Number of entries with unstaged changes
  unstagedCount: number;
  // Number of untracked entries
  untrackedCount: number;
}>; }>>; };
```

### `insert_walkthrough_tangents`

Inserts one or more answer steps as children of an existing walkthrough step. New child labels are derived automatically by appending '.N' to the parent label: the first tangent under '3' becomes '3.1', the next '3.2', and so on. The popup auto-navigates to the first inserted step. Clears the inline loading spinner so the user can ask another question. After this tool returns, immediately call await_walkthrough_question again with the same walkthroughId.

exec tool declaration:
```ts
declare const tools: { mcp__idea__insert_walkthrough_tangents(args: {
  // JSON array of walkthrough items to insert as children. For file walkthroughs, use the same item shape as show_walkthrough_items. For diff walkthroughs, use diff item fields from show_diff_walkthrough_items: 'text', 'diffId', 'diffFile', 'diffSide', and 'line'. Verify line numbers against the actual file or exact diff side before calling.
  items: string;
  // Label of the parent step the user asked the question under (e.g. '3' or '3.1'). Must match the parentLabel reported by await_walkthrough_question.
  parentLabel: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // The walkthroughId returned by show_walkthrough_items.
  walkthroughId: string;
}): Promise<CallToolResult>; };
```

### `introspect_schema`

Introspects a database schema, loading its metadata (tables, columns, indexes, etc.)
into the local model. Use this when a schema's `isIntrospected` flag is false
and you need to investigate the schema's structure, or to refresh stale metadata.
Returns the schema identifier with updated introspection status.

exec tool declaration:
```ts
declare const tools: { mcp__idea__introspect_schema(args: {
  // The unique ID of a database connection.
  connectionId: string;
  // Name of the database the schema belongs to. Might be empty if DBMS has no databases, but only schemas.
  // Together with `schemaName` property forms qualified schema name that uniquely identifies the schema within the connection.
  databaseName: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Name of the schema.
  schemaName: string;
}): Promise<CallToolResult<{
  // Name of the database the schema belongs to. Might be empty if DBMS has no databases, but only schemas.
  // Together with `schemaName` property forms qualified schema name that uniquely identifies the schema within the connection.
  databaseName: string;
  // Whether the schema metadata has been loaded (introspected).
  isIntrospected: boolean;
  // Name of the schema.
  schemaName: string;
}>>; };
```

### `lint_files`

Analyzes the specified files for errors and warnings using IntelliJ's inspections.
Use this tool to lint several files after editing them.
Returns per-file problems with severity, description, and location information.
Batch responses may include file entries with `timedOut: true` and empty `problems` when individual files exceed the available budget.
File entries with a `notAnalyzedReason` indicate files that could not be analyzed (e.g., outside project content roots, excluded, or unsupported file type).
Top-level `more: true` means the batch is incomplete.
`min_severity` must be `warning` or `error`; defaults to `warning`.
Note: Only analyzes files within the project directory.
Note: Lines and Columns are 1-based.

exec tool declaration:
```ts
declare const tools: { mcp__idea__lint_files(args: {
  // List of project-relative files to analyze. Duplicate paths are ignored after normalization.
  files: Array<string>;
  // Minimum severity to include: `warning` or `error`. Defaults to `warning`.
  min_severity?: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Timeout in milliseconds
  timeout?: number;
}): Promise<CallToolResult<{ items?: Array<{
  filePath: string;
  notAnalyzedReason?: string | null;
  problems?: Array<{ column: number; description: string; line: number; lineText: string; severity: string; }>;
  // Indicates whether the operation was timed out. 'true' value may mean that the results may be incomplete or partial. 'false', 'null' or missing value means that the operation has not been timed out.
  timedOut?: boolean | null;
}>; more?: boolean; }>>; };
```

### `list_database_connections`

Retrieves a list of configured database connections or data sources in the project.

exec tool declaration:
```ts
declare const tools: { mcp__idea__list_database_connections(args: {
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ connections: Array<{
  // DBMS description.
  dbms: string;
  // JDBC driver description. Empty if driver is not set.
  driverName: string;
  // The unique ID of a database connection.
  id: string;
  // True if the connection is a DDL data source, false otherwise.
  // DDL data source is a collection of SQL scripts which define database schema and
  // doesn't correspond to a live connection to a particular DBMS
  isDDL: boolean;
  // Human-readable name of a database connection or data source.
  name: string;
  // Whether the data source is read-only.
  readOnly: boolean;
}>; }>>; };
```

### `list_database_schemas`

Retrieves a list of database schemas in the specified database connection.

exec tool declaration:
```ts
declare const tools: { mcp__idea__list_database_schemas(args: {
  // The unique ID of a database connection.
  connectionId: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ schemas: Array<{
  // Name of the database the schema belongs to. Might be empty if DBMS has no databases, but only schemas.
  // Together with `schemaName` property forms qualified schema name that uniquely identifies the schema within the connection.
  databaseName: string;
  // Whether the schema metadata has been loaded (introspected).
  isIntrospected: boolean;
  // Name of the schema.
  schemaName: string;
}>; }>>; };
```

### `list_directory_tree`

Provides a tree representation of the specified directory in the pseudo graphic format like `tree` utility does.
Use this tool to explore the contents of a directory or the whole project.
You MUST prefer this tool over listing directories via command line utilities like `ls` or `dir`.

exec tool declaration:
```ts
declare const tools: { mcp__idea__list_directory_tree(args: {
  // Path relative to the project root
  directoryPath: string;
  // Maximum recursion depth
  maxDepth?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Timeout in milliseconds
  timeout?: number;
}): Promise<CallToolResult<{
  errors: Array<string>;
  // Indicates whether the operation was timed out. 'true' value may mean that the results may be incomplete or partial. 'false', 'null' or missing value means that the operation has not been timed out.
  listingTimedOut?: boolean | null;
  traversedDirectory: string;
  tree: string;
}>>; };
```

### `list_recent_sql_queries`

Retrieves a list of recent (including currently running) queries for the given database connection.
Do not use this tool for DDL data sources as they have no underlying DBMS connection.

exec tool declaration:
```ts
declare const tools: { mcp__idea__list_recent_sql_queries(args: {
  // The unique ID of a database connection.
  connectionId: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ queries: Array<{
  // Text of the query
  queryText: string;
  // The unique ID of a query session.
  sessionId: number;
  // Current state of the query (running, cancelling, finished, etc.).
  state: "SUBMITTED" | "RUNNING" | "CANCELLING" | "FINISHED";
  // Completion status of the query (success, finished with error, cancelled, etc.).
  status: "SUCCESS" | "WARNING" | "ERROR" | "CANCELLED" | "UNKNOWN";
  // Time spent on running the query (in milliseconds).
  timeSpentMs: number;
}>; }>>; };
```

### `list_schema_object_kinds`

Retrieves supported schema object kinds (e.g., table, view, routine) for the given database connection.

exec tool declaration:
```ts
declare const tools: { mcp__idea__list_schema_object_kinds(args: {
  // The unique ID of a database connection.
  connectionId: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ objectKinds: Array<{
  // Object kind unique code.
  code: string;
  // Human-readable name of the object kind.
  name: string;
}>; }>>; };
```

### `list_schema_objects`

Retrieves a list of database objects within the given schema.

exec tool declaration:
```ts
declare const tools: { mcp__idea__list_schema_objects(args: {
  // The unique ID of a database connection.
  connectionId: string;
  // Name of the database the schema belongs to. Might be empty if DBMS has no databases, but only schemas.
  // Together with `schemaName` property forms qualified schema name that uniquely identifies the schema within the connection.
  databaseName: string;
  // Object kind to filter by (e.g., table, view). If null, returns all objects in the schema.
  kind?: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Name of the schema.
  schemaName: string;
}): Promise<CallToolResult<{
  // Schema information.
  schemaInfo: {
  // Name of the database the schema belongs to. Might be empty if DBMS has no databases, but only schemas.
  // Together with `schemaName` property forms qualified schema name that uniquely identifies the schema within the connection.
  databaseName: string;
  // Whether the schema metadata has been loaded (introspected).
  isIntrospected: boolean;
  // Name of the schema.
  schemaName: string;
};
  // List of schema objects (e.g., tables, views, routines).
  schemaObjects: Array<{
  // Code of the object kind (see `list_schema_object_kinds` tool).
  kind: string;
  // Name of the object.
  name: string;
}>;
}>>; };
```

### `open_file_in_editor`

Opens the specified file in the JetBrains IDE editor.
Requires a filePath parameter containing the path to the file to open.
The file path can be absolute or relative to the project root.

exec tool declaration:
```ts
declare const tools: { mcp__idea__open_file_in_editor(args: {
  // Path relative to the project root
  filePath: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult>; };
```

### `preview_table_data`

Previews data of the table, view, materialized view or other table-like object using given database connection.
Do not use this tool for DDL data sources as they have no underlying DBMS connection.
Returns table content in CSV format

exec tool declaration:
```ts
declare const tools: { mcp__idea__preview_table_data(args: {
  // The unique ID of a database connection.
  connectionId: string;
  // Name of the database the schema belongs to. Might be empty if DBMS has no databases, but only schemas.
  // Together with `schemaName` property forms qualified schema name that uniquely identifies the schema within the connection.
  databaseName: string;
  // Maximum number of rows to return. Default is 10. You must NOT pass zero or negative value for this argument.
  maxRowCount?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Name of the schema.
  schemaName: string;
  // Name of the table.
  tableName: string;
}): Promise<CallToolResult<{
  // Error description if the query failed at the database; empty otherwise. Treat any non-empty value here as a failure regardless of `text`.
  errorMessage?: string | null;
  // ID of the cached query result set. Pass it to `fetch_query_result` to retrieve additional pages. Empty if the error happens before getting any result.
  resultSetId: string;
  // Rendered query result in CSV format, or a status message if the query returned no rows. Empty if the query failed.
  text?: string | null;
}>>; };
```

### `read_file`

Reads a file in the project directory or from any project dependency or other project source root.
        Can read sources inside Jar/Jrt files and decompile Java class files inside Jar/Jrt files or on disk. 
        Returns numbered lines (1-indexed) as text.
        By default, reads up to 2000 lines starting from the beginning of the file.
        The maximum accepted limit is 5000 lines.

exec tool declaration:
```ts
declare const tools: { mcp__idea__read_file(args: {
  // Path to the file. Supports project-relative paths, paths with '..', absolute paths, archive entries like '/path/lib.jar!/pkg/Foo.class', and URLs such as 'file://', 'jar://', and 'jrt://'. Any path returned from the other tools can be passed as is (e.g. paths from 'search_*' tools).
  file_path: string;
  // Maximum number of lines to return (default: 2000, max: 5000)
  limit?: number;
  // 1-based line number to start reading from
  offset?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult>; };
```

### `recognize_ij_module_kind`

Recognizes the effective Split Mode module kind for a plugin.xml or content-module descriptor
using the same DevKit analysis as the remdev inspections.
Returns the effective kind and the reasoning used to compute it.
Possible kinds include `shared`, `frontend`, `backend`, `monolith`, and `mixed`.

exec tool declaration:
```ts
declare const tools: { mcp__idea__recognize_ij_module_kind(args: {
  // Path relative to the project root
  descriptorPath: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{
  // Descriptor path that was analyzed, relative to the project root when provided that way.
  descriptorPath: string;
  // Effective Split Mode module kind. One of: `shared`, `frontend`, `backend`, `monolith`, `mixed`.
  kindId: string;
  // Resolved IntelliJ module name that owns the descriptor.
  moduleName: string;
  // Reasoning produced by the same DevKit module-kind analysis used by the remdev inspections.
  reasoning: string;
}>>; };
```

### `reformat_file`

Reformats the specified files in the JetBrains IDE.
Use this tool to apply code formatting rules to files identified by their project-relative paths.

exec tool declaration:
```ts
declare const tools: { mcp__idea__reformat_file(args: {
  // List of project-relative files to reformat. Duplicate paths are ignored after normalization.
  files: Array<string>;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult>; };
```

### `rename_refactoring`

Renames a symbol (variable, function, class, etc.) in the specified file.
        Use this tool to perform rename refactoring operations. 
        
        The `rename_refactoring` tool is a powerful, context-aware utility. Unlike a simple text search-and-replace, 
        it understands the code's structure and will intelligently update ALL references to the specified symbol throughout the project,
        ensuring code integrity and preventing broken references. It is ALWAYS the preferred method for renaming programmatic symbols.

        Requires three parameters:
            - pathInProject: The relative path to the file from the project's root directory (e.g., `src/api/controllers/userController.js`)
            - symbolName: The exact, case-sensitive name of the existing symbol to be renamed (e.g., `getUserData`)
            - newName: The new, case-sensitive name for the symbol (e.g., `fetchUserData`).
            
        Returns a success message if the rename operation was successful.
        Returns an error message if the file or symbol cannot be found or the rename operation failed.

exec tool declaration:
```ts
declare const tools: { mcp__idea__rename_refactoring(args: {
  // New name for the symbol
  newName: string;
  // Path relative to the project root
  pathInProject: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Name of the symbol to rename
  symbolName: string;
}): Promise<CallToolResult>; };
```

### `run_inspection_kts`

Compiles an inspection.kts script and runs it against a target file.
    Returns compilation errors if any, or the list of problems found by the inspection.
    Use this tool to test inspection.kts scripts during development.

exec tool declaration:
```ts
declare const tools: { mcp__idea__run_inspection_kts(args: {
  // Relative path of the target file inside project to analyze (e.g., 'src/my/package/Example.kt'
  contextPath: string;
  // The inspection.kts script content to compile and run
  inspectionKtsCode: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // The content of the target file to analyze. If not provided, the file must exist in the project.
  targetFileContent?: string;
}): Promise<CallToolResult<{ compilationErrorDetails?: string | null; compilationStatus?: string | null; compilationSuccess: boolean; foundProblems?: Array<{ elementText?: string | null; endOffset?: number | null; highlightType: string; lineNumber: number; message: string; startOffset?: number | null; }>; inspectionResultMessage?: string | null; }>>; };
```

### `search_file`

Searches for files by glob pattern within the project.
Use this tool when you need to match file paths using glob syntax.

Glob patterns are relative to the project root.
Examples: "**/*.kt", "src/**/Foo*.java", "build.gradle.kts".
Patterns without '/' are treated as "**/pattern".
Paths are optional additional glob filters relative to the project root.

exec tool declaration:
```ts
declare const tools: { mcp__idea__search_file(args: {
  // Whether to include excluded/ignored files in results
  includeExcluded?: boolean;
  // Maximum number of results to return
  limit?: number;
  // Optional list of project-relative glob patterns to filter results. Supports '!' excludes. Trailing '/' expands to '**'. Patterns without '/' are treated as '**/pattern'. Empty strings are ignored.
  paths?: Array<string>;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Glob pattern to search for
  q: string;
}): Promise<CallToolResult<{ items?: Array<{ endColumn?: number | null; endLine?: number | null; filePath: string; startColumn?: number | null; startLine?: number | null; }>; more?: boolean; partialResultReason?: string | null; }>>; };
```

### `search_regex`

Searches for regex matches within project files.
Use this tool when you need regex search with match coordinates.
Results include match coordinates when available (1-based line/column, end exclusive).

Paths are glob patterns relative to the project root.
Examples: ["src/**", "!**/test/**"], ["**/*.kt"], ["foo/"].

exec tool declaration:
```ts
declare const tools: { mcp__idea__search_regex(args: {
  // Maximum number of results to return
  limit?: number;
  // Optional list of project-relative glob patterns to filter results. Supports '!' excludes. Trailing '/' expands to '**'. Patterns without '/' are treated as '**/pattern'. Empty strings are ignored.
  paths?: Array<string>;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Regex pattern to search for
  q: string;
}): Promise<CallToolResult<{ items?: Array<{ endColumn?: number | null; endLine?: number | null; filePath: string; startColumn?: number | null; startLine?: number | null; }>; more?: boolean; partialResultReason?: string | null; }>>; };
```

### `search_symbol`

Searches for symbols (classes, methods, fields).
Use this tool for semantic lookup by identifier fragments.
Results include match coordinates when available (1-based line/column, end exclusive).
For call graph questions, use this only to locate the target symbol, then pass the fully qualified callable name
to `analyze_calls` instead of using text search or Find Usages manually.

Paths are glob patterns relative to the project root.
By default this searches project symbols only.
If you don't find a suitable result, try again with include_external=true to search SDK and library symbols too.

exec tool declaration:
```ts
declare const tools: { mcp__idea__search_symbol(args: {
  // Whether to include SDK and library symbols. Disabled by default; if nothing suitable is found, try again with include_external=true.
  include_external?: boolean;
  // Maximum number of results to return
  limit?: number;
  // Optional list of project-relative glob patterns to filter results. Supports '!' excludes. Trailing '/' expands to '**'. Patterns without '/' are treated as '**/pattern'. Empty strings are ignored.
  paths?: Array<string>;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Symbol query text
  q: string;
}): Promise<CallToolResult<{ items?: Array<{ endColumn?: number | null; endLine?: number | null; filePath: string; startColumn?: number | null; startLine?: number | null; }>; more?: boolean; partialResultReason?: string | null; }>>; };
```

### `search_text`

Searches for a text substring within project files.
Use this tool for fast text search with match coordinates.
Results include match coordinates when available (1-based line/column, end exclusive).

Paths are glob patterns relative to the project root.
Examples: ["src/**", "!**/test/**"], ["**/*.kt"], ["foo/"].

exec tool declaration:
```ts
declare const tools: { mcp__idea__search_text(args: {
  // Maximum number of results to return
  limit?: number;
  // Optional list of project-relative glob patterns to filter results. Supports '!' excludes. Trailing '/' expands to '**'. Patterns without '/' are treated as '**/pattern'. Empty strings are ignored.
  paths?: Array<string>;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Text to search for
  q: string;
}): Promise<CallToolResult<{ items?: Array<{ endColumn?: number | null; endLine?: number | null; filePath: string; startColumn?: number | null; startLine?: number | null; }>; more?: boolean; partialResultReason?: string | null; }>>; };
```

### `show_diff_walkthrough_items`

Shows and stores a diff walkthrough anchored to IntelliJ IDEA diff viewers. Use this when the user asks about changes, a PR, a review, a commit, a branch comparison, a patch, or 'what changed'. Do not use this for general code explanation unless the user specifically wants the explanation in terms of a change. All items in one call must target Git commit-backed file diffs; do not mix file walkthrough items and diff walkthrough items. Do not submit file contents. Submit commit hashes for the two file revisions to compare. After this tool returns, immediately call await_walkthrough_question with the returned walkthroughId.

exec tool declaration:
```ts
declare const tools: { mcp__idea__show_diff_walkthrough_items(args: {
  // Short human-readable description shown in the project walkthrough history. Use a concise phrase that helps the user recognize this walkthrough later.
  description: string;
  // JSON object with 'diffs' and 'items'. 'diffs' supplies Git revisions to compare: 'id', 'file', 'leftCommit', and 'rightCommit'; for renames, use 'leftFile' and 'rightFile' instead of 'file'. 'items' is an array with 'text', 'diffId', 'diffFile', 'diffSide', and 'line'. 'diffSide' is 'left' or 'right'. 'line' is 1-based in that side's full file text at that commit, not the patch hunk line. Use 'right' for added or modified new code and 'left' for removed old code. For PRs, pass the merge-base commit as 'leftCommit' and the PR head commit as 'rightCommit'. Verify every line by inspecting that exact file at that exact commit before calling.
  payload: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult>; };
```

### `show_walkthrough_items`

Shows and stores a file walkthrough anchored to normal project files. Use this when the user asks how code works, wants an architecture tour, asks for onboarding, or needs an explanation of existing behavior. Do not use this for PR review, branch review, commit review, or 'what changed' requests; use show_diff_walkthrough_items instead. Accepts one or more walkthrough items; the user can cycle through them with Previous and Next buttons. The walkthrough is saved to this project's history. Top-level items are auto-labeled '1', '2', '3', etc. The returned message includes a walkthroughId; pass it to await_walkthrough_question to react to follow-up questions the user types into the popup. After this tool returns, immediately call await_walkthrough_question with that walkthroughId. Do not stop after showing the walkthrough; the waiting tool call is required for the plugin to deliver user questions back to you.

exec tool declaration:
```ts
declare const tools: { mcp__idea__show_walkthrough_items(args: {
  // Short human-readable description shown in the project walkthrough history. Use a concise phrase that helps the user recognize this walkthrough later.
  description: string;
  // JSON array of walkthrough items to display, e.g. [{"text":"Note 1","file":"src/Foo.kt","line":10},{"text":"Note 2"}]. Each item requires 'text'; 'file' (path relative to project root) and 'line' (1-based) are optional. The 'line' value is a line in the current full file, not a diff hunk line, so it must be accurate. Verify line numbers by reading the actual file before calling this tool — do not estimate from diffs or memory.
  items: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult>; };
```

### `skill_search`

Unified project search with explicit mode.
Modes:
- file: glob path search
- text: literal content search
- regex: regex content search
- symbol: semantic symbol lookup

Symbol search is project-focused by default.
If you do not find a suitable symbol, try again with include_external=true to search SDK and library symbols too.

exec tool declaration:
```ts
declare const tools: { mcp__idea__skill_search(args: {
  // Include excluded/ignored files (supported only for mode=file)
  includeExcluded?: boolean;
  // Whether to include SDK and library symbols for mode=symbol. Disabled by default; if nothing suitable is found, try again with include_external=true.
  include_external?: boolean;
  // Maximum results to return
  limit?: number;
  // Search mode: file|text|regex|symbol.
  mode: string;
  // Optional project-relative glob filters. Supports '!'-excludes and trailing '/'.
  paths?: Array<string>;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Search query. For mode=file this is a glob pattern.
  q: string;
}): Promise<CallToolResult>; };
```

### `test_database_connection`

Checks whether a specific database connection is valid and reachable.
Do not use this tool for DDL data sources as they have no underlying DBMS connection.
Returns connection diagnostic info.

exec tool declaration:
```ts
declare const tools: { mcp__idea__test_database_connection(args: {
  // The unique ID of a database connection.
  id: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{
  // Detailed information about database connection such as DBMS type, version and JDBC driver.
  connectionInfo: string;
  // Shows if the connection is problematic (yes/no/unknown).
  hasProblems: "YES" | "NO" | "UNSURE";
  // Summary of connection attempt result. In case of a failure, contains DBMS-provided error description.
  summary: string;
}>>; };
```

### `validate_inspection_kts`

Validates an inspection.kts script against specification examples.
    Compiles the inspection and runs it against positive/negative examples.
    Returns compilation status and detailed verification results.
    
    Positive examples should trigger the inspection (problems expected).
    Negative examples should NOT trigger the inspection (no problems expected on forbidden lines).
    
    Returns overall success, per-example results, and aggregation statistics.

exec tool declaration:
```ts
declare const tools: { mcp__idea__validate_inspection_kts(args: {
  // The inspection.kts script content to compile and validate
  inspectionKtsCode: string;
  // Path to specification with examples to validate against.
  pathToSpecification: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{ compilationErrorDetails?: string | null; compilationStatus: string; compilationSuccess: boolean; negativeExample?: Array<{ example: { fileWithCodeToAnalyze: string; pathInProject: string; problemFreeRange?: Array<{ end: number; start: number; }> | null; }; executionStatus: string; falsePositive?: Array<{ end: number; start: number; }> | null; trueNegative?: Array<{ end: number; start: number; }> | null; }> | null; optionalNegativeExample?: Array<{ example: { fileWithCodeToAnalyze: string; pathInProject: string; problemFreeRange?: Array<{ end: number; start: number; }> | null; }; executionStatus: string; falsePositive?: Array<{ end: number; start: number; }> | null; trueNegative?: Array<{ end: number; start: number; }> | null; }> | null; optionalPositiveExample?: Array<{ example: { expectedProblemRanges?: Array<{ end: number; start: number; }> | null; fileWithCodeToAnalyze: string; pathInProject: string; }; executionStatus: string; falseNegative?: Array<{ end: number; start: number; }> | null; truePositive?: Array<{ end: number; start: number; }> | null; }> | null; overallSuccess: boolean; positiveExample?: Array<{ example: { expectedProblemRanges?: Array<{ end: number; start: number; }> | null; fileWithCodeToAnalyze: string; pathInProject: string; }; executionStatus: string; falseNegative?: Array<{ end: number; start: number; }> | null; truePositive?: Array<{ end: number; start: number; }> | null; }> | null; validationStatus: string; }>>; };
```

### `xdebug_control_session`

Controls the execution of a debug session.
Use this tool to step through code, resume execution, pause, or stop the debug session.

Preconditions:
- A debug session must exist.
- `STEP_*` and `RESUME` require a suspended session.

Actions:
- STEP_INTO: Step into the next method call
- STEP_OVER: Step over the current line
- STEP_OUT: Step out of the current method
- RESUME: Resume program execution until the next breakpoint
- PAUSE: Pause program execution
- STOP: Stop the debug session
- WAIT_FOR_PAUSE: Wait until the session pauses (breakpoint hit or paused manually)
- DRAIN_EVENTS: Drain tracepoint outputs for the session (breakpoint errors are drained for all actions)

Important notes:
- If the program is running, use WAIT_FOR_PAUSE or PAUSE before STEP_* / RESUME.
- Use a current `sessionId` from `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session stops, times out, or disappears, refresh the session list before the next session-scoped call.
- RESUME does NOT set breakpoints. If there are no enabled breakpoints (or none will be hit next), the program may run to completion and the session may stop without pausing.
- After RESUME, call WAIT_FOR_PAUSE to confirm the next suspension. If WAIT_FOR_PAUSE times out, consider PAUSE and re-check breakpoints.
- `DRAIN_EVENTS` also requires an existing session; do not reuse a stale `sessionId` after the session has terminated.

Next call:
- After `RESUME`, call `xdebug_control_session(action=WAIT_FOR_PAUSE)`.
- After a paused result, call `xdebug_get_stack` / `xdebug_get_frame_values` / `xdebug_evaluate_expression`.

Status values in the result:
- running: Program is executing
- paused: Execution is suspended (breakpoint, step, or manual pause); paused results also include `frameValues`, a current-frame snapshot in `xdebug_get_frame_values(depth=0)` format when available
- stopped: Debug session has terminated
- `breakpointErrorsTail` is returned for any action
- `tracepointOutputsTail` is returned only for `DRAIN_EVENTS`

Event support scope:
- Breakpoint error and tracepoint output events are currently reported only by JVM-based debuggers (Java, Kotlin, etc.).
- On other debugger backends these event tails can be empty even when breakpoints/logging are configured.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_control_session(args: {
  // Action to perform: STEP_INTO, STEP_OVER, STEP_OUT, RESUME, PAUSE, STOP, WAIT_FOR_PAUSE, DRAIN_EVENTS. Event draining is currently populated only by JVM-based debuggers (Java, Kotlin, etc.).
  action: "STEP_INTO" | "STEP_OVER" | "STEP_OUT" | "RESUME" | "PAUSE" | "STOP" | "WAIT_FOR_PAUSE" | "DRAIN_EVENTS";
  // Compatibility flag. Returned events are always removed from internal buffers, regardless of this value.
  clearEventsAfterRead?: boolean;
  // Maximum number of latest events to drain per event list. For DRAIN_EVENTS this limit is applied independently to breakpointErrorsTail and tracepointOutputsTail. Default: 100.
  eventsLimit?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null.
  sessionId?: string;
  // Timeout in milliseconds to wait for action completion. Guidance: STEP_* / PAUSE usually 5000-15000; WAIT_FOR_PAUSE usually 30000-120000 depending on workload and breakpoints. Default: 30000.
  timeout?: number;
}): Promise<CallToolResult<{
  // Latest drained breakpoint error events. Returned for any control_session action. Indicates errors in breakpoints configuration like invalid conditional/log expressions. Currently populated only by JVM-based debuggers (Java, Kotlin, etc.).
  breakpointErrorsTail?: Array<{
  // Canonical breakpoint ID when available.
  breakpointId?: string | null;
  // Additional event context details when available.
  details?: string | null;
  // Breakpoint file path as provided by debugger when available.
  filePath?: string | null;
  // 1-based breakpoint line when available.
  line?: number | null;
  // Primary event message.
  message: string;
  // Debugger session identifier.
  sessionId: string;
  // ISO-8601 timestamp when the event was recorded.
  timestampIso: string;
  // Unix timestamp in milliseconds when the event was recorded.
  timestampMs: number;
  // Event type (BREAKPOINT_ERROR or TRACEPOINT_OUTPUT).
  type: "BREAKPOINT_ERROR" | "TRACEPOINT_OUTPUT";
}> | null;
  // Whether breakpoints are globally muted for this debugger session.
  breakpointsMuted?: boolean;
  // Snapshot of current frame values when the session is paused, using the same text format as `xdebug_get_frame_values` with `depth=0`.
  frameValues?: string | null;
  // Additional context message for timeout/already-paused/already-stopped situations.
  message?: string | null;
  // Current source position when the session is paused (if available).
  newPosition?: {
  // 1-based column number when available.
  column?: number | null;
  // File path as provided by the debugger (usually VirtualFile.url).
  filePath: string;
  // 1-based line number.
  line: number;
} | null;
  // Session state after the control action.
  status: "running" | "paused" | "stopped";
  // Latest drained tracepoint output events. Returned only for DRAIN_EVENTS action. Currently populated only by JVM-based debuggers (Java, Kotlin, etc.).
  tracepointOutputsTail?: Array<{
  // Canonical breakpoint ID when available.
  breakpointId?: string | null;
  // Additional event context details when available.
  details?: string | null;
  // Breakpoint file path as provided by debugger when available.
  filePath?: string | null;
  // 1-based breakpoint line when available.
  line?: number | null;
  // Primary event message.
  message: string;
  // Debugger session identifier.
  sessionId: string;
  // ISO-8601 timestamp when the event was recorded.
  timestampIso: string;
  // Unix timestamp in milliseconds when the event was recorded.
  timestampMs: number;
  // Event type (BREAKPOINT_ERROR or TRACEPOINT_OUTPUT).
  type: "BREAKPOINT_ERROR" | "TRACEPOINT_OUTPUT";
}> | null;
}>>; };
```

### `xdebug_evaluate_expression`

Evaluates an expression in the context of the current stack frame.
Use this tool to compute values, call methods, or inspect expressions during debugging.

Preconditions:
- Session must be suspended.
- Evaluation must be supported for the selected frame/language.
- `expression` must be a valid expression in the language of the current frame.

The result is returned as:
- depth == 0: just the presentation of the evaluated expression
- depth > 0: the presentation plus a pseudo-graphics tree of its children up to the requested depth

Input rules:
- Pass raw expression text exactly as the debugger evaluator should parse it.
- Do not pass JSON-escaped payloads or literal escape sequences such as `\\"text\\"`.

Next call:
- If expression confirms hypothesis, continue with `xdebug_control_session(STEP_*|RESUME)`.
- If more detail is needed, inspect related values via `xdebug_get_frame_values` / `xdebug_get_value_by_path`.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_evaluate_expression(args: {
  // Maximum depth for expanding children of the evaluated result (0 = value only, 1 = immediate children, 2 = children + grandchildren, etc.). Default: 0.
  depth?: number;
  // Expression to evaluate in the current context. Pass raw expression text in the language of the current frame; do not pass JSON-escaped payloads or literal backslash-escaped quoted text.
  expression: string;
  // Stack frame index as integer (0 = top frame). Obtain this from the current paused `xdebug_get_stack` result; do not reuse a cached frame index after `RESUME`, `STEP_*`, `xdebug_run_to_line`, or any change in paused location. If null, uses the top frame. Default: null.
  frameIndex?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null.
  sessionId?: string;
}): Promise<CallToolResult>; };
```

### `xdebug_get_debugger_status`

Returns the current status of the debugger including all active debug sessions.
Use this tool to get an overview of all running debug sessions and their states.

Preconditions:
- None.

Returns explicit `sessions[]` and `activeSessionId`.

Next call:
- If no sessions are running, call `xdebug_start_debugger_session`.
- If multiple sessions are active, use returned `id` as `sessionId` in subsequent calls.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_get_debugger_status(args: {
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{
  // Identifier of the active session, if any.
  activeSessionId?: string | null;
  // All currently known debug sessions.
  sessions: Array<{
  // Whether breakpoints are globally muted for this debugger session.
  breakpointsMuted?: boolean;
  // Current source position for paused sessions, if available.
  currentPosition?: {
  // 1-based column number when available.
  column?: number | null;
  // File path as provided by the debugger (usually VirtualFile.url).
  filePath: string;
  // 1-based line number.
  line: number;
} | null;
  // Session identifier to use as `sessionId` in debugger calls. Uses session name by default; if duplicate names exist, format is `<sessionName>#<executionId>`.
  id: string;
  // Session display name.
  name: string;
  // Associated run configuration name when available.
  runConfigurationName?: string | null;
  // Current session state.
  state: "running" | "paused" | "stopped";
}>;
}>>; };
```

### `xdebug_get_frame_values`

Returns the values visible in the specified stack frame as a tree structure.
Use this tool to inspect local variables, parameters, and fields or other values available at a specific point in the call stack.

Preconditions:
- Session must be suspended.
- Frame index should come from the current paused `xdebug_get_stack` result (0 = top frame).

Format:
- Nodes that have children are marked with `+`.

Next call:
- Use `xdebug_get_value_by_path` to drill into nested fields.
- Use `xdebug_evaluate_expression` for computed checks in the same frame.
- Do not reuse a cached `frameIndex` after `RESUME`, `STEP_*`, `xdebug_run_to_line`, or any change in paused location.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_get_frame_values(args: {
  // Maximum depth for expanding nested objects (0 = no children (only frame variables), 1 = variables with first level children, 2 = two levels of children, etc.). Default: 0.
  depth?: number;
  // Stack frame index as integer (0 = top frame). Obtain this from the current paused `xdebug_get_stack` result; do not reuse a cached frame index after `RESUME`, `STEP_*`, `xdebug_run_to_line`, or any change in paused location. If null, uses the top frame. Default: null.
  frameIndex?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null.
  sessionId?: string;
}): Promise<CallToolResult>; };
```

### `xdebug_get_stack`

Returns the call stack for a thread in the debug session.
Use this tool to see the sequence of method calls that led to the current execution point.

Preconditions:
- Session must be suspended.

Behavior:
- `threadId` should come from `xdebug_get_threads` and matches the debugger thread display name (defaults to active thread).
- Includes frames even when source position is missing (file/line may be null).

Pagination:
- `offset`/`limit` are applied after collecting the full stack.

Frame fields include: index, file, line, isCurrent, presentation.
`file` is reported as provided by the debugger (no path normalization).

Next call:
- Use frame index from the current paused result in `xdebug_get_frame_values`, `xdebug_get_value_by_path`, or `xdebug_evaluate_expression`.
- Do not reuse a cached `frameIndex` after `RESUME`, `STEP_*`, `xdebug_run_to_line`, or any change in paused location.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_get_stack(args: {
  // Max frames to return. Default: 200.
  limit?: number;
  // Page offset. Default: 0.
  offset?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null.
  sessionId?: string;
  // Thread ID to get stack for. This value should come from `xdebug_get_threads` and matches the debugger thread display name, not an opaque numeric ID. If not specified, uses the current/active thread. Default: null.
  threadId?: string;
}): Promise<CallToolResult<{
  // Stack frames for the selected thread, ordered from top (index 0) to older frames.
  frames: Array<{
  // Source file path as provided by the debugger when available.
  file?: string | null;
  // 0-based frame index to use as `frameIndex` in other debugger tools.
  index: number;
  // Whether this is the currently selected frame.
  isCurrent: boolean;
  // 1-based source line when available.
  line?: number | null;
  // Rendered function/method frame label from debugger UI.
  presentation: string;
}>;
  // Thread identifier used to fetch this stack.
  threadId?: string | null;
  // Total frame count for the stack.
  totalFrames: number;
}>>; };
```

### `xdebug_get_threads`

Returns the list of threads in the debug session.
Use this tool to see all threads and their current status.

Preconditions:
- Session must be suspended.

Next call:
- Use `xdebug_get_stack` for the selected thread.

Pagination:
- `offset`/`limit` are applied after collecting all stacks.

Ordering:
- Active thread first.
- Remaining threads are sorted by descending stack depth.

Schema fields: id, name, state, isCurrent, additionalInfo, additionalInfoTooltip, frameCount.
`additionalInfo`/`additionalInfoTooltip` use additional display info when available.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_get_threads(args: {
  // Page size. Default: 50, max: 200.
  limit?: number;
  // Page offset. Default: 0.
  offset?: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null.
  sessionId?: string;
}): Promise<CallToolResult<{
  // Requested page limit.
  limit: number;
  // Requested page offset.
  offset: number;
  // Threads available in the suspended debug session (paginated).
  threads: Array<{
  // Additional thread display info when available.
  additionalInfo?: string | null;
  // Tooltip for additional thread display info when available.
  additionalInfoTooltip?: string | null;
  // Number of stack frames when available.
  frameCount?: number | null;
  // Thread identifier to pass as `threadId` in `get_stack`.
  id: string;
  // Whether this thread is currently selected.
  isCurrent: boolean;
  // Human-readable thread name.
  name: string;
  // Current thread status from debugger perspective.
  state: string;
}>;
  // Total known thread count.
  totalCount: number;
}>>; };
```

### `xdebug_get_value_by_path`

Gets the value of a nested object by following a path of property names.
Use this tool to drill down into complex objects and inspect their nested properties.

Preconditions:
- Session must be suspended.
- Path must be non-empty and refer to names visible in the selected frame/object.

The result is returned as:
- depth == 0: just the presentation of the value at the specified path
- depth > 0: the presentation of the value plus a pseudo-graphics tree of its children up to the requested depth

Example: To get the value of obj.field.subField, use path = ["obj", "field", "subField"].
For array/list indexers, pass the index token as a regular path element (child name), e.g.
items[0].name -> path = ["items", "[0]", "name"].
Use exact child names from the current paused `xdebug_get_frame_values` / previous `xdebug_get_value_by_path` output
because index node names may differ by language/debugger (for example, "[0]" vs "0").
Refresh `path` tokens after `RESUME`, `STEP_*`, `xdebug_run_to_line`, or any other change in paused location.

Next call:
- Use another `xdebug_get_value_by_path` call to continue drilling deeper.
- Use `xdebug_evaluate_expression` when direct name-path navigation is insufficient.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_get_value_by_path(args: {
  // Maximum depth for expanding children of the resolved value (0 = value only, 1 = immediate children, 2 = children + grandchildren, etc.). Default: 0.
  depth?: number;
  // Stack frame index as integer (0 = top frame). Obtain this from the current paused `xdebug_get_stack` result; do not reuse a cached frame index after `RESUME`, `STEP_*`, `xdebug_run_to_line`, or any change in paused location. If null, uses the top frame. Default: null.
  frameIndex?: number;
  // List of child names to navigate through, e.g. ['myObject', 'field', 'subField'] or ['items', '[0]', 'name']. Use exact node names from the current paused `xdebug_get_frame_values` / `xdebug_get_value_by_path` output and refresh stale path tokens after the paused location changes.
  path: Array<string>;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null.
  sessionId?: string;
}): Promise<CallToolResult>; };
```

### `xdebug_list_breakpoints`

Lists all breakpoints in the project or in a specific file.
Use this tool to see all currently set breakpoints and their properties.

Tip: Call this before RESUME to confirm there is at least one enabled breakpoint that is expected to be hit next.

Behavior:
- If `filePath` is provided, returns only breakpoints in that file.
- Returns rich attributes for each breakpoint (id, type, file, line, enabled, owner, condition, isLogMessage, isLogStack, temporary, suspendPolicy, hitCount).
- `breakpointsMuted` reports the session-wide debugger mute flag when a session is resolved; it does not change per-breakpoint `enabled` values.

Next call:
- If no suitable breakpoint exists, call `xdebug_set_breakpoint`.
- Then continue execution with `xdebug_control_session(action=RESUME)` and `xdebug_control_session(action=WAIT_FOR_PAUSE)`.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_list_breakpoints(args: {
  // Optional file path to filter breakpoints. Path to the file. Supports project-relative paths, paths with '..', absolute paths, archive entries like '/path/lib.jar!/pkg/Foo.class', and URLs such as 'file://', 'jar://', and 'jrt://'. Any path returned from the other tools can be passed as is (e.g. paths from 'search_*' tools). If not specified, returns all breakpoints. Default: null.
  filePath?: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null. Optional; when omitted, `breakpointsMuted` is returned only if exactly one active session exists.
  sessionId?: string;
}): Promise<CallToolResult<{
  // List of currently configured breakpoints.
  breakpoints: Array<{
  // Conditional expression for triggering breakpoint, if set.
  condition?: string | null;
  // Whether breakpoint is enabled.
  enabled: boolean;
  // File path of the breakpoint as provided by the debugger (usually file URL).
  file?: string | null;
  // Breakpoint hit count, 0 when unavailable.
  hitCount: number;
  // Canonical breakpoint ID (stable across list/remove).
  id: string;
  // Whether breakpoint logs source position when hit.
  isLogMessage: boolean;
  // Whether breakpoint logs stack trace when hit.
  isLogStack: boolean;
  // 1-based breakpoint line when available.
  line?: number | null;
  // Evaluate-and-log expression of the logpoint, if set (the value logged when the line is reached).
  logExpression?: string | null;
  // Breakpoint ownership marker: `agent` if created/updated by MCP toolset, otherwise `user`.
  owner: "user" | "agent";
  // Breakpoint suspend policy (all/thread/none).
  suspendPolicy: string;
  // Whether breakpoint is temporary.
  temporary: boolean;
  // Breakpoint type (line/exception/other).
  type: string;
}>;
  // Whether breakpoints are globally muted for the resolved debugger session.
  breakpointsMuted?: boolean;
  // Enabled count.
  enabledCount: number;
  // Total count.
  totalCount: number;
}>>; };
```

### `xdebug_remove_breakpoint`

Removes breakpoints filtered by owner and optional selectors.
Use this tool to remove previously set breakpoints.

Behavior:
- `owner` defaults to `agent`.
- If only `owner` is provided, removes all breakpoints of that owner.
- If `breakpointId` is provided, removes matching breakpoint(s) for the selected owner.
- If `filePath`+`line` are provided, removes matching line breakpoint(s) for the selected owner.
- If multiple selectors are provided, all of them are combined (logical AND).
- Idempotent: removing a non-existing breakpoint returns removed=false.
- To remove all breakpoints regardless of owner, call twice: once with `owner=user`, once with `owner=agent`.

Next call:
- Use `xdebug_list_breakpoints` to verify the remaining set.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_remove_breakpoint(args: {
  // Canonical breakpoint ID returned by `xdebug_set_breakpoint` or `xdebug_list_breakpoints`.
  breakpointId?: string;
  // Optional input: Path to the file. Supports project-relative paths, paths with '..', absolute paths, archive entries like '/path/lib.jar!/pkg/Foo.class', and URLs such as 'file://', 'jar://', and 'jrt://'. Any path returned from the other tools can be passed as is (e.g. paths from 'search_*' tools).
  filePath?: string;
  // Optional input: line number (1-based) of the breakpoint to remove.
  line?: number;
  // Breakpoint owner filter. Default: agent.
  owner?: "user" | "agent";
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
}): Promise<CallToolResult<{
  // Removed breakpoint ID when operation targeted one ID.
  breakpointId?: string | null;
  // Additional note when no matching breakpoint is found.
  message?: string | null;
  // Whether at least one breakpoint was removed.
  removed: boolean;
  // Number of breakpoints removed at the requested location.
  removedCount: number;
  // Current total number of breakpoints after removal.
  totalBreakpoints: number;
}>>; };
```

### `xdebug_run_to_line`

Resumes execution to a target line.
Use this tool to run until a specific source position without manually stepping.

Preconditions:
- Session must be suspended.
- Target file/line must be valid.

Outcome:
- paused: session paused at or after target.
- stopped: session terminated before pause.
- timeout: no pause/stop within timeout window.

Next call:
- If paused, call `xdebug_get_stack` / `xdebug_get_frame_values` / `xdebug_evaluate_expression`.
- If the session stopped or disappeared, refresh `sessionId` via `xdebug_get_debugger_status` before issuing another session-scoped call.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_run_to_line(args: {
  // Target source file path. Path to the file. Supports project-relative paths, paths with '..', absolute paths, archive entries like '/path/lib.jar!/pkg/Foo.class', and URLs such as 'file://', 'jar://', and 'jrt://'. Any path returned from the other tools can be passed as is (e.g. paths from 'search_*' tools).
  filePath: string;
  // Target line number (1-based).
  line: number;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null.
  sessionId?: string;
  // Timeout in milliseconds waiting for paused/stopped result. Default: 30000.
  timeout?: number;
}): Promise<CallToolResult<{
  // Current position when outcome is paused and position is known.
  currentPosition?: {
  // 1-based column number when available.
  column?: number | null;
  // File path as provided by the debugger (usually VirtualFile.url).
  filePath: string;
  // 1-based line number.
  line: number;
} | null;
  // Additional context message (timeout or errors).
  message?: string | null;
  // Outcome after attempting run-to-line (paused/stopped/timeout).
  outcome: "paused" | "stopped" | "timeout";
  // Session identifier.
  sessionId: string;
}>>; };
```

### `xdebug_set_breakpoint`

Creates or updates a breakpoint or a logpoint (a non-suspending "Evaluate and log" breakpoint).
Use this tool to set line breakpoints, set logpoints via `logExpression`, update existing breakpoints by ID, and control tracepoint/logging behavior.

Logpoints are the preferred, low-disturbance probe, and providing a `logExpression` is the most important input:
set `logExpression` together with `suspendPolicy=NONE` to evaluate an expression and log its result every time the line
is reached WITHOUT stopping execution — the primary way to capture runtime values, branch/reachability evidence, counts,
and identifiers. Read the logged output via `xdebug_control_session(action=DRAIN_EVENTS).tracepointOutputsTail`.

Targeting modes:
- By location: provide `filePath` + `line`, and omit `breakpointId` (or pass null). Do not use placeholder strings such as `""`, `"/"`, or `"__omit__"`.
- By ID: provide an existing opaque canonical `breakpointId` returned by `xdebug_set_breakpoint` or `xdebug_list_breakpoints` (optional `filePath`/`line` can relocate line breakpoints).
- Breakpoint mute-only: provide only `sessionId` and `breakpointsMuted`.
  Do not combine `breakpointsMuted` with breakpoint target or settings parameters.

Validation:
- In location mode, both `filePath` and `line` are required.
- In ID mode, breakpoint must exist and be uniquely identified by `breakpointId`.
- In location mode, `filePath` is relative to the project root, `line` is 1-based, and the target location must be executable.
- In breakpoint mute-only mode, a debugger session must be resolved from `sessionId` or the single active session.

Event reporting:
- Invalid `condition` expressions are reported asynchronously via `xdebug_control_session(...).breakpointErrorsTail`.
- Tracepoint output from breakpoints with `isLogMessage` and/or `isLogStack` is drained via `xdebug_control_session(action=DRAIN_EVENTS).tracepointOutputsTail`.
- Breakpoint-error and tracepoint-output reporting is currently supported only by JVM-based debuggers (Java, Kotlin, etc.).
- A successful `xdebug_set_breakpoint` response does not guarantee that `condition` or tracepoint expressions are valid; check later `breakpointErrorsTail` before relying on them.
- Successful line-breakpoint responses also include `lineText`, a truncated excerpt of the actual source line where the breakpoint now resides. Inspect it to confirm placement before resuming.

Apply semantics:
- Provided fields are applied as the resulting state for the target breakpoint.
- `condition=null` clears existing condition.
- `logExpression` sets the Evaluate-and-log expression that is evaluated and logged when the breakpoint is hit; `logExpression=null` clears it. Combine with `suspendPolicy=NONE` for a non-suspending logpoint (the preferred way to capture values). Keep it side-effect-free; evaluation errors surface in `breakpointErrorsTail`.
- `isLogMessage=true` logs breakpoint hit position.
- `isLogStack=true` logs current stack trace.
- If both flags are true, both position and stack are logged.
- With `isLogMessage`/`isLogStack` + `suspendPolicy=NONE`, the breakpoint behaves as a tracepoint.
- In ID mode, if `filePath`/`line` are provided for a line breakpoint, it is relocated (recreated) at the new location.
- In ID mode, for non-line breakpoints, `filePath`/`line` are ignored and reported in `message`.
- `breakpointsMuted` is a session-wide debugger flag; use it in a dedicated call with only `sessionId` and `breakpointsMuted`.
  It does not change the per-breakpoint `enabled` value.
- Any successful operation marks breakpoint as `agent` ownership (`mcpBreakpointMarker`).

Next call:
- Use returned `lineText` and/or `xdebug_list_breakpoints` to verify placement.
- Start/continue execution via `xdebug_start_debugger_session` or `xdebug_control_session(action=RESUME)`.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_set_breakpoint(args: {
  // Canonical breakpoint ID returned by `xdebug_set_breakpoint` or `xdebug_list_breakpoints`. If provided, the tool runs in ID mode. Omit this or pass null in location mode; do not use placeholder strings such as empty string or fake path-like values. Default: null.
  breakpointId?: string;
  // Session-wide breakpoint mute flag. When provided, call this tool with only `sessionId` plus `breakpointsMuted`; do not pass breakpointId, filePath, line, condition, logging, suspend, temporary, or enabled parameters. Default: null.
  breakpointsMuted?: boolean;
  // Optional condition expression - breakpoint will only trigger when this evaluates to true. Validation errors are reported asynchronously via xdebug_control_session(...).breakpointErrorsTail (JVM-based debuggers only). Default: null.
  condition?: string;
  // Whether breakpoint is enabled. Default: true.
  enabled?: boolean;
  // Path to the file. Supports project-relative paths, paths with '..', absolute paths, archive entries like '/path/lib.jar!/pkg/Foo.class', and URLs such as 'file://', 'jar://', and 'jrt://'. Any path returned from the other tools can be passed as is (e.g. paths from 'search_*' tools). Required only in location mode. Optional in ID mode to relocate line breakpoints.
  filePath?: string;
  // Whether to log breakpoint hit position (source location) when breakpoint is reached. In JVM-based debuggers output is available via xdebug_control_session(action=DRAIN_EVENTS).tracepointOutputsTail. Default: false.
  isLogMessage?: boolean;
  // Whether to log stack trace when breakpoint is reached. In JVM-based debuggers output is available via xdebug_control_session(action=DRAIN_EVENTS).tracepointOutputsTail. Default: false.
  isLogStack?: boolean;
  // 1-based line number. Required only in location mode. Optional in ID mode to relocate line breakpoints.
  line?: number;
  // The Evaluate-and-log expression. When set, its result is logged each time the breakpoint is hit, read via xdebug_control_session(action=DRAIN_EVENTS).tracepointOutputsTail. Combine with suspendPolicy=NONE to make a non-suspending logpoint - the preferred, most important way to capture runtime values without freezing threads. Keep it side-effect-free (no mutation, I/O, or iterator/stream advancement). logExpression=null clears it. Default: null.
  logExpression?: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null. Use with `breakpointsMuted` in a dedicated mute-only call; do not combine that call with breakpoint target or settings parameters.
  sessionId?: string;
  // Suspend policy: ALL, THREAD, NONE. Default: ALL.
  suspendPolicy?: "ALL" | "THREAD" | "NONE";
  // Temporary breakpoint (removed after first hit). Default: false.
  temporary?: boolean;
}): Promise<CallToolResult<{
  // Details of the newly added or updated breakpoint. Absent for breakpoint mute-only operations.
  added?: {
  // Conditional expression for triggering breakpoint, if set.
  condition?: string | null;
  // Whether breakpoint is enabled.
  enabled: boolean;
  // File path of the breakpoint as provided by the debugger (usually file URL).
  file?: string | null;
  // Breakpoint hit count, 0 when unavailable.
  hitCount: number;
  // Canonical breakpoint ID (stable across list/remove).
  id: string;
  // Whether breakpoint logs source position when hit.
  isLogMessage: boolean;
  // Whether breakpoint logs stack trace when hit.
  isLogStack: boolean;
  // 1-based breakpoint line when available.
  line?: number | null;
  // Evaluate-and-log expression of the logpoint, if set (the value logged when the line is reached).
  logExpression?: string | null;
  // Breakpoint ownership marker: `agent` if created/updated by MCP toolset, otherwise `user`.
  owner: "user" | "agent";
  // Breakpoint suspend policy (all/thread/none).
  suspendPolicy: string;
  // Whether breakpoint is temporary.
  temporary: boolean;
  // Breakpoint type (line/exception/other).
  type: string;
} | null;
  // Canonical breakpoint ID. Absent for breakpoint mute-only operations.
  breakpointId?: string | null;
  // Whether breakpoints are globally muted for the resolved debugger session.
  breakpointsMuted?: boolean;
  // Short excerpt of the actual source line where the breakpoint resides, truncated when needed. Present for line breakpoints only.
  lineText?: string | null;
  // Additional note when requested and actual positions differ.
  message?: string | null;
  // Previous canonical breakpoint ID when operation relocated an existing line breakpoint.
  previousBreakpointId?: string | null;
  // Current total number of breakpoints after operation.
  totalBreakpoints: number;
}>>; };
```

### `xdebug_set_variable`

Mutates a variable value by path in the selected stack frame.
Use this tool to change state during debugging.

Preconditions:
- Session must be suspended.
- Value must be modifiable.
- `path` should come from the current paused `xdebug_get_frame_values` / `xdebug_get_value_by_path` output.

Path format is the same as in `xdebug_get_value_by_path`.
`newValue` must be a raw expression in the language of the current frame, and it must be assignable to the target value by the debugger/evaluator.
Do not pass JSON-escaped payloads or literal escape sequences such as `\\"text\\"`.

Result:
- Returns oldValue/newValue/applied.
- Unsupported mutation returns an error with a textual message.

Next call:
- Re-read value via `xdebug_get_value_by_path` or `xdebug_get_frame_values` to confirm.

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_set_variable(args: {
  // Stack frame index as integer (0 = top frame). Obtain this from the current paused `xdebug_get_stack` result; do not reuse a cached frame index after `RESUME`, `STEP_*`, `xdebug_run_to_line`, or any change in paused location. If null, uses the top frame. Default: null.
  frameIndex?: number;
  // New value expression to assign. Pass raw expression text in the language of the current frame; it must be assignable to the target value by the debugger/evaluator. Do not pass JSON-escaped payloads or literal backslash-escaped quoted text.
  newValue: string;
  // Path to target value, same format as `xdebug_get_value_by_path`. Use exact node names from the current paused `xdebug_get_frame_values` / `xdebug_get_value_by_path` output and refresh stale path tokens after the paused location changes.
  path: Array<string>;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Debug session ID. Use the current ID returned by `xdebug_get_debugger_status` or `xdebug_start_debugger_session`. If a session has stopped, timed out, or disappeared, refresh the session list before reusing an old ID. Format: uses session name as ID by default; if multiple sessions share the same name, ID is `<sessionName>#<executionId>`. If null and exactly one active session exists, it is selected automatically. If multiple sessions are active and sessionId is omitted, the call fails. Default: null.
  sessionId?: string;
}): Promise<CallToolResult<{
  // Whether mutation was applied.
  applied: boolean;
  // Value after mutation.
  newValue: string;
  // Value before mutation.
  oldValue: string;
  // Variable path used for mutation.
  path: Array<string>;
}>>; };
```

### `xdebug_start_debugger_session`

Start a debugger session for either an existing run configuration by name or a code location
(`filePath` + `line`) in the current project.
Use this tool to start a debugger session.
Use this tool with either an existing run configuration name, or with `filePath` + `line`.
When using `filePath` + `line`, a line with a runnable method such as `main`, a test, or another executable
entry point will almost always work. If you are unsure which line to use, `get_run_configurations`
can help discover runnable locations in the file.
The session will be started and you can then use other debugger tools to control execution.

Preconditions:
- When using `configurationName`, pass the exact existing run configuration name; do not pass a test method name or other derived target identifier.
- When using `filePath` + `line`, point at a runnable code location such as `main`, a test, or another executable entry point.
- Set at least one breakpoint first; otherwise the program may run to completion without pausing.
- Pass either `configurationName`, or `filePath` together with `line`. These modes are mutually exclusive.

Behavior:
- Waits for session creation up to `timeout`.
- Applies a grace wait (`graceWaitMs`) after the session starts and returns refreshed state.
- Optional launch overrides (`programArguments`, `workingDirectory`, `envs`) are applied only for this debug launch and are not persisted.
- `get_run_configurations` is the source of truth for override support: only pass launch overrides when the selected run configuration reports `supportsDynamicLaunchOverrides=true`.
- Do not pass these override parameters unless you explicitly need to change the configured launch values for this debug launch.
- Missing/null override parameters keep existing run configuration values unchanged.
- For string overrides (`programArguments`, `workingDirectory`), missing/null or empty string (`""`) keeps the existing value unchanged.
- Pass a whitespace-only string such as `" "` to clear an existing value for this debug launch.

Next call:
- `xdebug_control_session(action=WAIT_FOR_PAUSE)` to wait for first suspension.
- After pause, call `xdebug_get_stack` and `xdebug_get_frame_values` (or `xdebug_evaluate_expression`) for runtime evidence.

Returns a flat result with debugger session metadata plus the execution snapshot fields from the launch:
- `sessionId`, `name`, `status`, and optional `runConfigurationName`
- `output` preview and optional `fullOutputPath`
- optional `exitCode` when process termination is already known

exec tool declaration:
```ts
declare const tools: { mcp__idea__xdebug_start_debugger_session(args: {
  // Name of the existing run configuration to debug.
  configurationName?: string;
  // Optional environment variable overrides for this launch only. Pass this only when the selected run configuration reports `supportsDynamicLaunchOverrides=true` in `get_run_configurations`. Missing/null keeps existing env unchanged; when provided, values are merged over existing env.
  envs?: { [key: string]: string; };
  // File path relative to the project root. Provide together with `line` to start debugging from a code location.
  filePath?: string;
  // Grace wait in milliseconds after session starts to refresh state. Default: 2000.
  graceWaitMs?: number;
  // 1-based line number for `filePath`. Provide together with `filePath` and do not combine with `configurationName`.
  line?: number;
  // Optional program arguments override for this launch only. Pass this only when the selected run configuration reports `supportsDynamicLaunchOverrides=true` in `get_run_configurations`. Missing/null or empty string keeps the existing value; whitespace-only string clears it.
  programArguments?: string;
  // The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
  // In the case you know only the current working directory you can use it as the project path.
  // If you're not aware about the project path you can ask user about it.
  projectPath?: string;
  // Timeout in milliseconds to wait for the debug session to start. Default: 60000.
  timeout?: number;
  // Optional working directory override for this launch only. Pass this only when the selected run configuration reports `supportsDynamicLaunchOverrides=true` in `get_run_configurations`. Missing/null or empty string keeps the existing value; whitespace-only string clears it.
  workingDirectory?: string;
}): Promise<CallToolResult<{
  // Whether breakpoints are globally muted for this debugger session.
  breakpointsMuted?: boolean;
  // Process exit code. Absent when the tool returns before observing process termination, for example when the debuggee continues running after the session starts.
  exitCode?: number | null;
  // Path to a temp file containing the full raw output. The file may continue growing while the process is still running and remains available while the IDE is running.
  fullOutputPath?: string | null;
  // Human-readable session name.
  name: string;
  // Captured process output snapshot. When additional output exists, `<truncated>` is appended to the preview.
  output: string;
  // Associated run configuration name, if available.
  runConfigurationName?: string | null;
  // Session identifier to use as `sessionId` in subsequent debugger calls. Uses session name by default; if duplicate names exist, format is `<sessionName>#<executionId>`.
  sessionId: string;
  // Current session state.
  state: "running" | "paused" | "stopped";
}>>; };
```

