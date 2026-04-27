# Workspace Goals

All goals are available in IntelliJ's Maven tool window
under **Plugins > ws** and **Plugins > ike**.

Draft goals preview changes without executing. Publish goals execute.
Use `-Dpublish=true` on any draft goal as a shortcut for the publish variant.

## Workspace Management

| Goal | Description |
|------|-------------|
| `ws:create` | Create a new workspace (scaffold + git init) |
| `ws:add` | Add a subproject repo (prompts for URL) |
| `ws:init` | Clone/initialize all subprojects |
| `ws:fix` | Sync workspace.yaml versions from actual POMs |
| `ws:graph` | Print dependency graph (text or DOT format) |
| `ws:stignore` | Generate Syncthing ignore rules |
| `ws:scaffold-upgrade-draft` | Preview workspace scaffold upgrades |
| `ws:scaffold-upgrade-publish` | Apply scaffold upgrades |
| `ws:remove` | Remove a subproject (prompts for name) |
| `ws:help` | List all ws: goals with descriptions |

## Verification

| Goal | Description |
|------|-------------|
| `ws:verify` | Check manifest, parents, BOM cascade, VCS state |
| `ws:verify-convergence` | Full verify + transitive dependency convergence (slow) |
| `ws:overview` | Workspace overview (manifest, graph, status, cascade) |
| `ws:check-branch` | Warn when a subproject branch deviates from workspace.yaml |

## Version Alignment

| Goal | Description |
|------|-------------|
| `ws:align-draft` | Preview inter-subproject POM/branch alignment |
| `ws:align-publish` | Apply alignment to POMs and/or branches |
| `ws:set-parent-draft` | Preview parent-POM version cascade |
| `ws:set-parent-publish` | Apply parent-POM version cascade (auto-commits) |
| `ws:versions-upgrade-draft` | Preview version upgrades against the configured ruleset |
| `ws:versions-upgrade-publish` | Apply the workspace version-upgrade plan |

## Branch Coordination

| Goal | Description |
|------|-------------|
| `ws:switch-draft` | Preview switching subprojects to a coordinated branch |
| `ws:switch-publish` | Switch subprojects to a coordinated branch |
| `ws:update-feature-draft` | Preview rebasing a feature branch onto main |
| `ws:update-feature-publish` | Rebase a feature branch onto main |

## Feature Branching

| Goal | Description |
|------|-------------|
| `ws:feature-start-draft` | Preview feature branch |
| `ws:feature-start-publish` | Create feature branch across components |
| `ws:feature-finish-merge-draft` | Preview no-ff merge |
| `ws:feature-finish-merge-publish` | No-ff merge (preserves history) |
| `ws:feature-finish-squash-draft` | Preview squash merge |
| `ws:feature-finish-squash-publish` | Squash merge (single commit) |
| `ws:feature-abandon-draft` | Preview abandoning a feature branch |
| `ws:feature-abandon-publish` | Delete feature branch across components |

Feature-finish options: `-Dpush=true` pushes to origin, `-DkeepBranch=false` deletes the branch.

## Release & Checkpoint

| Goal | Description |
|------|-------------|
| `ws:release-draft` | Preview what would be released |
| `ws:release-publish` | Execute workspace release (`-DgithubRepo=` for GitHub Release) |
| `ws:release-status` | Diagnose state of any in-flight workspace release |
| `ws:checkpoint-draft` | Preview checkpoint (tag all components) |
| `ws:checkpoint-publish` | Execute checkpoint (pushes branches + tags to origin) |
| `ws:post-release` | Bump to next development version |
| `ws:release-notes` | Generate release notes from GitHub milestone |

## VCS Bridge (Syncthing multi-machine)

| Goal | Description |
|------|-------------|
| `ws:sync` | Pull then push across the workspace (the daily sync op) |
| `ws:commit` | Commit across repos (stages all by default; `-DstagedOnly` to opt out) |
| `ws:pull` | Git pull --rebase across all subprojects |
| `ws:push` | Push all subprojects (warns about uncommitted changes) |
| `ws:report` | Aggregate ws:* goal reports into a single document |

## Branch Cleanup

| Goal | Description |
|------|-------------|
| `ws:cleanup-draft` | List merged/stale feature branches |
| `ws:cleanup-publish` | Delete merged feature branches |

## Build Goals (ike:)

| Goal | Description |
|------|-------------|
| `ike:release-draft` | Preview single-repo release |
| `ike:release-publish` | Execute single-repo release |
| `ike:generate-bom` | Generate BOM with resolved versions |
| `ike:deploy-site-draft` | Preview site deployment |
| `ike:deploy-site-publish` | Deploy project site |
| `ike:clean-site` | Remove a deployed site |
| `ike:register-site-draft` | Preview IKE Network site registration |
| `ike:register-site-publish` | Register project on IKE Network |
| `ike:deregister-site-draft` | Preview deregistration |
| `ike:deregister-site-publish` | Deregister project from IKE Network |
| `ike:help` | List all ike: goals with descriptions |

---
*See `ws:help` and `ike:help` for full details.*
