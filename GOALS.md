# Workspace Goals

All goals are available in IntelliJ's Maven tool window
under **Plugins > ws** and **Plugins > ike**.

Draft goals preview changes without executing. Publish goals execute.
Use `-Dpublish=true` on any draft goal as a shortcut for the publish variant.

## Workspace Management

| Goal | Description |
|------|-------------|
| `ws:create` | Create a new workspace (scaffold + git init) |
| `ws:add` | Add a component repo (prompts for URL) |
| `ws:init` | Clone/initialize all components |
| `ws:remove` | Remove a component (prompts for name) |
| `ws:fix` | Sync workspace.yaml versions from actual POMs |
| `ws:graph` | Print dependency graph (text or `-Dformat=dot`) |
| `ws:stignore` | Generate Syncthing ignore rules |
| `ws:upgrade-draft` | Preview workspace convention upgrades |
| `ws:upgrade-publish` | Apply convention upgrades |
| `ws:help` | List all ws: goals with descriptions |

## Verification

| Goal | Description |
|------|-------------|
| `ws:verify` | Check manifest, parents, BOM cascade, VCS state |
| `ws:verify-convergence` | Full verify + transitive dependency convergence (slow) |
| `ws:overview` | Workspace overview (manifest, graph, status, cascade) |
| `ws:cascade` | Show downstream impact of a component change |
| `ws:check-branch` | Verify all components are on the expected branch |

## Version Alignment

| Goal | Description |
|------|-------------|
| `ws:align-draft` | Preview inter-component version changes |
| `ws:align-publish` | Apply version alignment to POMs |
| `ws:pull` | Git pull --rebase across all components |

## Feature Branching

| Goal | Description |
|------|-------------|
| `ws:feature-start-draft` | Preview creating a feature branch |
| `ws:feature-start-publish` | Create feature branch across components |
| `ws:feature-finish-merge-draft` | Preview no-ff merge |
| `ws:feature-finish-merge-publish` | No-ff merge (preserves history) |
| `ws:feature-finish-squash-draft` | Preview squash merge |
| `ws:feature-finish-squash-publish` | Squash merge (single commit) |
| `ws:feature-finish-rebase-draft` | Preview rebase |
| `ws:feature-finish-rebase-publish` | Rebase + fast-forward (linear history) |
| `ws:feature-abandon-draft` | Preview/execute abandoning a feature branch |

Feature-finish options: `-Dpush=true` pushes to origin, `-DkeepBranch=false` deletes the branch.

## Release & Checkpoint

| Goal | Description |
|------|-------------|
| `ws:release-draft` | Preview what would be released |
| `ws:release-publish` | Execute workspace release (`-DgithubRepo=` for GitHub Release) |
| `ws:checkpoint-draft` | Preview checkpoint (tag all components) |
| `ws:checkpoint-publish` | Execute checkpoint (pushes branches + tags to origin) |
| `ws:post-release` | Bump to next development version |
| `ws:release-notes` | Generate release notes from GitHub milestone |

## VCS Bridge (Syncthing multi-machine)

| Goal | Description |
|------|-------------|
| `ws:sync` | Reconcile state after machine switch |
| `ws:commit` | Commit across workspace (includes workspace root) |
| `ws:push` | Push across workspace |

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
