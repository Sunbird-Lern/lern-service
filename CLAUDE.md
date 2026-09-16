# CLAUDE.md — lern-service

User/Org (identity, RBAC), LMS (batches, enrollment), and Notification APIs. Stack basics (Java 11 / Play 3.0.5 / Pekko / Maven, Kong-injected identity headers, actor-per-request, port 9000, shared datastores) are in the **workspace-root CLAUDE.md** — don't duplicate.

Multi-module Maven project. Root `pom.xml` always builds `core/`; Maven **profiles** select service modules (`-Puserorg`, `-Plms`, `-Pnotification`, or `-Plern` for the unified runtime — the default deploy target). Details live in the rules below.

## Rules

Detailed guidance is split into `.claude/rules/`:

| Rule file | Load behavior | Covers |
|---|---|---|
| `build-and-layout.md` | Always-on | Module layout, `core/` modules, Maven profiles, build & run commands, Keycloak key basepath, local setup order, cross-cutting gotchas |
| `userorg.md` | Path-scoped (`modules/userorg/**`) | UserOrg modules, responsibilities, macOS transport fix |
| `lms.md` | Path-scoped (`modules/lms/**`) | LMS modules, responsibilities, macOS transport fix |
| `notification.md` | Path-scoped (`modules/notification/**`) | Notification modules, responsibilities, macOS transport fix |
