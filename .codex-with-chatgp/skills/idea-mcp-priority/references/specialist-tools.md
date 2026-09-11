# Specialist Tools

## Database

| Tool | Use it when | Key usage rule |
| --- | --- | --- |
| `list_database_connections` | Starting database work | First establish available connection IDs/names. |
| `test_database_connection` | Reachability or credentials are uncertain | Test before querying when connection health is unknown. |
| `list_database_schemas` | A connection is selected | Discover schemas instead of guessing them. |
| `list_schema_object_kinds` | You need object categories within a schema | Use before listing a specific object category. |
| `list_schema_objects` | You need tables/views/routines and their names | Scope by the confirmed connection, schema, and object kind. |
| `get_database_object_description` | Column/type/DDL-level detail is needed | Inspect a confirmed object before writing SQL against it. |
| `preview_table_data` | A small data sample is needed | Use a bounded preview; do not retrieve more data than necessary. |
| `introspect_schema` | Structured, broad schema metadata is needed | Prefer this over multiple object calls when it matches the question. |
| `execute_sql_query` | Target connection/schema/object shape is already confirmed | Treat as state-changing when SQL can mutate; do not issue guessed or destructive SQL. |
| `fetch_query_result` | A query result is paged or deferred | Use the query/session ID returned by execution. |
| `list_recent_sql_queries` | You need to inspect recent query activity | Read history before retrying/cancelling a query. |
| `cancel_sql_query` | A known query session must stop | Use only the exact session ID returned by query tools. |
| `create_database_connection`, `edit_database_connection` | The user explicitly asks to create/edit a data source | Confirm DBMS, JDBC URL, and whether an immediate connection test is wanted. |

Default order: connections → optional health check → schemas → object kinds → objects → description/preview → query.

## Inspection-KTS and PSI

| Tool | Use it when | Key usage rule |
| --- | --- | --- |
| `generate_inspection_kts_api` | Writing an inspection-KTS script | Generate current API information before authoring. |
| `generate_inspection_kts_examples` | You need working inspection-KTS patterns | Use as a targeted example source, not normal code navigation. |
| `generate_psi_tree` | PSI structure of a source target matters | Generate only for the relevant file/range. |
| `validate_inspection_kts` | Before executing an inspection-KTS script | Validate first and fix diagnostics rather than running invalid scripts. |
| `run_inspection_kts` | The validated script is ready to analyze code | Scope the inspection and interpret results before changing code. |

## Interactive walkthroughs

| Tool | Use it when | Key usage rule |
| --- | --- | --- |
| `show_walkthrough_items` | A user requested an IDEA walkthrough | Use structured items with target locations and explanations. |
| `show_diff_walkthrough_items` | A walkthrough should explain a diff | Use when the changed code is the teaching target. |
| `await_walkthrough_question` | A walkthrough popup is active | Call immediately after showing; on `waiting-expired`, call again with the same ID. |
| `insert_walkthrough_tangents` | The user asked a follow-up inside the walkthrough | Insert the answer, then resume the wait loop. |

Keep the loop active until the wait tool returns `dismissed`.

## Router observations (IDEA 2026.2)

- Calling a database or walkthrough tool without its required arguments returns a structured `isError: true` response listing the missing parameters; it does not throw at the router boundary. This is a safe way to confirm a contract when no connection or walkthrough has been authorized.
- Do not use a parameterless `xdebug_remove_breakpoint` merely as a probe: it defaults to `owner=agent` and can remove **all** agent-owned breakpoints. In an empty project it reports `removed: false`, but that outcome must not be assumed. Use `xdebug_list_breakpoints` for a read-only probe instead.
- Debugger tools do not validate in one consistent order. `xdebug_get_threads`, `xdebug_get_stack`, and `xdebug_get_frame_values` first report that no active session exists, while `xdebug_get_value_by_path` and `xdebug_evaluate_expression` first report their missing required argument. Always bind a live session before treating such errors as parameter feedback.
- A missing walkthrough ID is rejected before any walkthrough UI is shown; this can safely confirm the three follow-up tool contracts without creating an interactive popup.

## Parameter fields

`!` is schema-required and `?` is optional. Pass known `projectPath`. Router flag casing is tool-specific in IDEA 2026.2; use the invocation reference's observed-casing rule rather than blindly converting camelCase to snake_case. JSON arrays/objects such as `items` and `payload` are passed as JSON text in a router command.

### Database fields

| Tool | Fields |
| --- | --- |
| `cancel_sql_query` | `sessionId!`, `projectPath?` |
| `create_database_connection` | `name!`, `dbms!`, `url!`, `needToCheckDs!`, `projectPath?` |
| `edit_database_connection` | `connectionId!`, `dbms!`, `url!`, `needToCheckDs!`, `projectPath?` |
| `execute_sql_query` | `connectionId!`, `databaseName!`, `schemaName!`, `queryText!`, `projectPath?` |
| `fetch_query_result` | `resultSetId!`, `offset!`, `projectPath?` |
| `get_database_object_description` | `connectionId!`, `databaseName!`, `schemaName!`, `kind!`, `objectName!`, `projectPath?` |
| `introspect_schema` | `connectionId!`, `databaseName!`, `schemaName!`, `projectPath?` |
| `list_database_connections` | `projectPath?` |
| `list_database_schemas` | `connectionId!`, `projectPath?` |
| `list_recent_sql_queries` | `connectionId!`, `projectPath?` |
| `list_schema_object_kinds` | `connectionId!`, `projectPath?` |
| `list_schema_objects` | `connectionId!`, `databaseName!`, `schemaName!`, `kind?`, `projectPath?` |
| `preview_table_data` | `connectionId!`, `databaseName!`, `schemaName!`, `tableName!`, `maxRowCount?`, `projectPath?` |
| `test_database_connection` | `id!`, `projectPath?` |

### Inspection-KTS, PSI, and walkthrough fields

| Tool | Fields |
| --- | --- |
| `generate_inspection_kts_api` | `language!`, `wrapInTags?`, `projectPath?` |
| `generate_inspection_kts_examples` | `language?`, `includeAdditionalExamples?`, `projectPath?` |
| `generate_psi_tree` | `code!`, `language!`, `projectPath?` |
| `run_inspection_kts` | `contextPath!`, `inspectionKtsCode!`, `targetFileContent?`, `projectPath?` |
| `validate_inspection_kts` | `inspectionKtsCode!`, `pathToSpecification!`, `projectPath?` |
| `show_walkthrough_items` | `description!`, `items!`, `projectPath?` |
| `show_diff_walkthrough_items` | `description!`, `payload!`, `projectPath?` |
| `await_walkthrough_question` | `walkthroughId!`, `projectPath?` |
| `insert_walkthrough_tangents` | `walkthroughId!`, `parentLabel!`, `items!`, `projectPath?` |
