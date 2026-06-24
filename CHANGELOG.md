# Changelog

## Unreleased

### Breaking Changes

* Renamed the statement type enum from `Stmt.STMT_TYPE` to `Stmt.StmtType`.
* Removed duplicate per-statement `StmtType` string constants, such as `ArrayAppendStmt.StmtType`; statement type names now come from `Stmt.StmtType#getTypeName()`.
* Removed the unused `MakeObjectStmt#getStmtType()` helper.

### Changed

* Updated statement deserialization to use `Stmt.StmtType#getTypeName()` as the single source of statement type names.
