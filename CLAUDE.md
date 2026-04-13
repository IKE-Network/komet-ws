# komet-ws

## First Steps

Run `mvn ws:init` to clone components, then `mvn validate` to unpack
full build standards into `.claude/standards/`.

## Build

```bash
mvn clean verify -DskipTests -T4   # compile + javadoc
mvn clean verify -T4                # full build with tests
```

## Key Conventions

- Maven 4 with POM modelVersion 4.1.0
- `<subprojects>` (not `<modules>`) for aggregation
- All projects use `--enable-preview` (Java 25)
- Parent: `network.ike.pipeline:ike-parent` (from ike-pipeline)

## POM Changes — Use Tooling, Not Manual Edits

- **Parent version bumps:** Use `ws:align-publish` — it updates all component
  parent versions and inter-component dependency versions in one pass
- **GroupId migration:** Use the OpenRewrite recipe:
  `mvn rewrite:run -Drewrite.activeRecipes=network.ike.MigrateGroupIds`
- **Dependency version changes:** Use `ws:align-publish` for workspace-internal
  dependencies. For external deps, edit the POM directly
- **Never use `sed`, `awk`, or regex-based POM manipulation** — use
  `ws:align`, OpenRewrite, or the PomRewriter API in ike-workspace-maven-plugin

## Prohibited Patterns

These are the most critical rules. Full standards are in `.claude/standards/MAVEN.md`
after building.

- **Never use `maven-antrun-plugin`** — use a proper Maven goal or `exec-maven-plugin`
  with an external script
- **Never use `build-helper-maven-plugin` for multi-execution property chaining** —
  write a proper Maven goal in `ike-maven-plugin` instead
- **Never embed shell commands inline in POM** — extract to a named script
- **Never use `git add -A` or `git add .`** — stage specific files
- **Never use `sed` or manual text replacement on POM files** — use
  `ws:align`, OpenRewrite recipes, or the Maven 4 model API

## Project-Specific Notes

See `CLAUDE-komet-ws.md` for workspace-specific information.
See `.claude/standards/` (after `mvn validate`) for full build standards.
