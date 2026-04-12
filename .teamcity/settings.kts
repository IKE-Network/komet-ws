import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
 * TeamCity Kotlin DSL for komet-ws workspace aggregator pipeline.
 *
 * Topology:
 *   1. AggregatorDeploy  — mvn deploy (Mac Studio)
 *   2a. InstallerMacOS   — mvn verify -pl komet-desktop (Mac Studio, .pkg)
 *   2b. InstallerWindows — mvn verify -pl komet-desktop (Windows agent, .msi)
 *   3. PublishRelease    — gh release create/upload + Zulip notification (Mac Studio)
 *
 * Triggers on:
 *   - Release tags:     refs/tags/v*
 *   - Checkpoint tags:  refs/tags/checkpoint/*  (pending ike-issues#66)
 *
 * PLUG-AND-PLAY: To reuse for another workspace, update the github.repo
 * parameter and the VCS root URL.
 */

version = "2024.12"

project {
    description = "komet-ws workspace — multi-platform build & release pipeline"

    vcsRoot(WorkspaceVcs)

    buildType(AggregatorDeploy)
    buildType(InstallerMacOS)
    buildType(InstallerWindows)
    buildType(PublishRelease)

    params {
        param("github.repo", "IKE-Network/komet-ws")
        password("env.GITHUB_TOKEN", "", display = ParameterDisplay.HIDDEN,
            label = "GitHub PAT (repo scope on IKE-Network)")
        password("env.ZULIP_BOT_EMAIL", "", display = ParameterDisplay.HIDDEN,
            label = "Zulip bot email")
        password("env.ZULIP_BOT_TOKEN", "", display = ParameterDisplay.HIDDEN,
            label = "Zulip bot API key")
        param("zulip.url", "https://ike.zulipchat.com")
        param("zulip.stream", "builds")
        password("env.CODESIGN_KEYCHAIN_PASSWORD", "", display = ParameterDisplay.HIDDEN,
            label = "macOS login keychain password (unlocks codesign cert)")
    }
}

object WorkspaceVcs : GitVcsRoot({
    name = "komet-ws"
    url = "https://github.com/IKE-Network/komet-ws.git"
    branch = "refs/heads/main"
    branchSpec = """
        +:refs/tags/v*
        +:refs/tags/checkpoint/*
    """.trimIndent()
    pollInterval = 60
})

object AggregatorDeploy : BuildType({
    name = "Aggregator Deploy"
    description = "Build all modules and deploy artifacts to Nexus"

    vcs {
        root(WorkspaceVcs)
    }

    steps {
        maven {
            name = "Deploy to Nexus"
            goals = "clean deploy"
            runnerArgs = "-DskipTests -T4"
            mavenVersion = auto()
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:refs/tags/v*
                +:refs/tags/checkpoint/*
            """.trimIndent()
        }
    }

    features {
        perfmon {}
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Mac")
        contains("teamcity.agent.jvm.os.arch", "aarch64")
    }
})

object InstallerMacOS : BuildType({
    name = "Installer macOS"
    description = "Build macOS .pkg installer via JReleaser/jpackage"

    vcs {
        root(WorkspaceVcs)
    }

    steps {
        maven {
            name = "Build macOS Installer"
            goals = "clean verify"
            runnerArgs = "-pl komet-desktop -DskipTests"
            mavenVersion = auto()
        }
    }

    artifactRules = "komet-desktop/target/**/*.pkg => installers"

    dependencies {
        snapshot(AggregatorDeploy) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    features {
        perfmon {}
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Mac")
        contains("teamcity.agent.jvm.os.arch", "aarch64")
    }
})

object InstallerWindows : BuildType({
    name = "Installer Windows"
    description = "Build Windows .msi installer via JReleaser/jpackage"

    vcs {
        root(WorkspaceVcs)
    }

    steps {
        maven {
            name = "Build Windows Installer"
            goals = "clean verify"
            runnerArgs = "-pl komet-desktop -DskipTests"
            mavenVersion = auto()
        }
    }

    artifactRules = "komet-desktop/target/**/*.msi => installers"

    dependencies {
        snapshot(AggregatorDeploy) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }

    features {
        perfmon {}
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Windows")
    }
})

object PublishRelease : BuildType({
    name = "Publish GitHub Release"
    description = "Create/update GitHub Release with installers, notify Zulip, prune old checkpoints"

    vcs {
        root(WorkspaceVcs)
    }

    params {
        param("env.PATH", "/opt/homebrew/bin:%env.PATH%")
    }

    steps {
        script {
            name = "Publish to GitHub Releases"
            scriptContent = """
                #!/bin/bash
                set -euo pipefail

                REPO="%github.repo%"
                TAG_NAME="${'$'}{BRANCH_NAME#refs/tags/}"
                echo "Publishing: ${'$'}TAG_NAME → ${'$'}REPO"

                PRERELEASE_FLAG=""
                if [[ "${'$'}TAG_NAME" == checkpoint/* ]]; then
                    PRERELEASE_FLAG="--prerelease"
                    echo "Checkpoint build — creating pre-release"
                fi

                mkdir -p artifacts
                cp installers/*.pkg artifacts/ 2>/dev/null || echo "No .pkg found"
                cp installers/*.msi artifacts/ 2>/dev/null || echo "No .msi found"

                ASSET_COUNT=${'$'}(ls artifacts/ 2>/dev/null | wc -l | tr -d ' ')
                if [[ "${'$'}ASSET_COUNT" -eq 0 ]]; then
                    echo "##teamcity[buildProblem description='No installer artifacts found']"
                    exit 1
                fi

                echo "Uploading ${'$'}ASSET_COUNT installer(s)"

                gh release create "${'$'}TAG_NAME" ${'$'}PRERELEASE_FLAG \
                    --repo "${'$'}REPO" \
                    --title "${'$'}TAG_NAME" \
                    --generate-notes \
                    artifacts/* \
                || gh release upload "${'$'}TAG_NAME" \
                    --repo "${'$'}REPO" \
                    --clobber \
                    artifacts/*

                echo "Published: https://github.com/${'$'}REPO/releases/tag/${'$'}TAG_NAME"
            """.trimIndent()
        }

        script {
            name = "Notify Zulip"
            scriptContent = """
                #!/bin/bash
                set -euo pipefail

                TAG_NAME="${'$'}{BRANCH_NAME#refs/tags/}"
                RELEASE_URL="https://github.com/%github.repo%/releases/tag/${'$'}TAG_NAME"

                TYPE="Release"
                if [[ "${'$'}TAG_NAME" == checkpoint/* ]]; then
                    TYPE="Checkpoint"
                fi

                if [[ -n "${'$'}{ZULIP_BOT_EMAIL:-}" && -n "${'$'}{ZULIP_BOT_TOKEN:-}" ]]; then
                    curl -sS -X POST "%zulip.url%/api/v1/messages" \
                        -u "${'$'}ZULIP_BOT_EMAIL:${'$'}ZULIP_BOT_TOKEN" \
                        -d "type=stream" \
                        -d "to=%zulip.stream%" \
                        -d "topic=Komet Builds" \
                        -d "content=**${'$'}TYPE: ${'$'}TAG_NAME** — installers ready: ${'$'}RELEASE_URL"
                    echo "Zulip notification sent"
                else
                    echo "Zulip credentials not configured — skipping notification"
                fi
            """.trimIndent()
        }

        script {
            name = "Prune old checkpoint pre-releases"
            scriptContent = """
                #!/bin/bash
                set -euo pipefail

                REPO="%github.repo%"
                KEEP=5
                echo "Pruning checkpoint pre-releases (keeping last ${'$'}KEEP)"

                OLD_RELEASES=${'$'}(gh release list --repo "${'$'}REPO" \
                    --json tagName,isPrerelease,createdAt \
                    --jq '[.[] | select(.isPrerelease)] | sort_by(.createdAt) | reverse | .['${'$'}KEEP':] | .[].tagName')

                if [[ -z "${'$'}OLD_RELEASES" ]]; then
                    echo "Nothing to prune"
                    exit 0
                fi

                echo "${'$'}OLD_RELEASES" | while read -r TAG; do
                    echo "Deleting: ${'$'}TAG"
                    gh release delete "${'$'}TAG" --repo "${'$'}REPO" --cleanup-tag -y
                done
            """.trimIndent()
        }
    }

    dependencies {
        snapshot(InstallerMacOS) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(InstallerWindows) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        artifacts(InstallerMacOS) {
            buildRule = lastSuccessful()
            artifactRules = "installers/*.pkg => installers"
        }
        artifacts(InstallerWindows) {
            buildRule = lastSuccessful()
            artifactRules = "installers/*.msi => installers"
        }
    }

    features {
        perfmon {}
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Mac")
        contains("teamcity.agent.jvm.os.arch", "aarch64")
    }
})
