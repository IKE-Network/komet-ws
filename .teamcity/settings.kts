import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
 * TeamCity Kotlin DSL for komet-ws workspace aggregator pipeline.
 *
 * Topology:
 *   1. AggregatorDeploy  — mvn deploy (Mac Studio)
 *   2a. InstallerMacOS   — ws:init + mvn install with signing/notarization (Mac Studio, .pkg)
 *   2b. InstallerWindows — ws:init + mvn install (Windows agent, .msi)
 *   3. PublishRelease    — gh release create/upload + Zulip notification (Mac Studio)
 *
 * Triggers on:
 *   - Release tags:     refs/tags/v*
 *   - Checkpoint tags:  refs/tags/checkpoint/*
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
            label = "GitHub PAT (contents:write on IKE-Network)")
        password("env.ZULIP_BOT_EMAIL", "", display = ParameterDisplay.HIDDEN,
            label = "Zulip bot email")
        password("env.ZULIP_BOT_TOKEN", "", display = ParameterDisplay.HIDDEN,
            label = "Zulip bot API key")
        param("zulip.url", "https://ike.zulipchat.com")
        param("zulip.stream", "builds")
        password("env.CODESIGN_KEYCHAIN_PASSWORD", "", display = ParameterDisplay.HIDDEN,
            label = "macOS login keychain password (unlocks codesign cert)")
        password("env.APPLE_ID", "", display = ParameterDisplay.HIDDEN,
            label = "Apple ID email for notarization")
        password("env.APPLE_APP_SPECIFIC_PWD", "", display = ParameterDisplay.HIDDEN,
            label = "Apple app-specific password for notarization")
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
    useTagsAsBranches = true
})

object AggregatorDeploy : BuildType({
    name = "Aggregator Deploy"
    description = "Build all modules and deploy artifacts to Nexus"

    vcs {
        root(WorkspaceVcs)
    }

    steps {
        script {
            name = "Clone components and deploy to Nexus"
            scriptContent = """
                #!/bin/bash
                set -euo pipefail
                ./mvnw network.ike.pipeline:ike-workspace-maven-plugin:init -f pom.xml
                ./mvnw clean deploy -DskipTests -T4 -f pom.xml
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            branchFilter = """
                +:refs/tags/v*
                +:refs/tags/checkpoint/*
            """.trimIndent()
            param("teamcity.vcsTrigger.runBuildInNewEmptyBranch", "true")
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
    description = "Build macOS .pkg installer with Apple code signing and notarization"

    vcs {
        root(WorkspaceVcs)
    }

    steps {
        script {
            name = "Clone components and build macOS installer"
            scriptContent = """
                #!/bin/bash
                set -euo pipefail

                # Unlock system keychain for codesign access (headless agent)
                if [[ -n "${'$'}{CODESIGN_KEYCHAIN_PASSWORD:-}" ]]; then
                    security unlock-keychain -p "${'$'}CODESIGN_KEYCHAIN_PASSWORD" /Library/Keychains/System.keychain
                    echo "System keychain unlocked"
                fi

                ./mvnw network.ike.pipeline:ike-workspace-maven-plugin:init -f pom.xml
                ./mvnw -P jlink-standard,create-desktop-installer -T 1C -DskipTests clean install -f pom.xml
            """.trimIndent()
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
        script {
            name = "Clone components and build Windows installer"
            scriptContent = """
                if exist ike-bom rmdir /s /q ike-bom
                if exist tinkar-core rmdir /s /q tinkar-core
                if exist tinkar-composer rmdir /s /q tinkar-composer
                if exist rocks-kb rmdir /s /q rocks-kb
                if exist komet rmdir /s /q komet
                if exist komet-desktop rmdir /s /q komet-desktop
                call mvn -s C:\BuildAgent\settings.xml -U network.ike.pipeline:ike-workspace-maven-plugin:init -f pom.xml
                if errorlevel 1 exit /b 1
                call mvn -s C:\BuildAgent\settings.xml -P jlink-standard,create-desktop-installer -T 1C -DskipTests clean install -f pom.xml
                if errorlevel 1 exit /b 1
            """.trimIndent()
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
                TAG_NAME="%teamcity.build.vcs.branch.KometWs_KometWs%"
                TAG_NAME="${'$'}{TAG_NAME#refs/tags/}"
                echo "Publishing: ${'$'}TAG_NAME to ${'$'}REPO"

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

                TAG_NAME="%teamcity.build.vcs.branch.KometWs_KometWs%"
                TAG_NAME="${'$'}{TAG_NAME#refs/tags/}"
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
            buildRule = sameChainOrLastFinished()
            artifactRules = "installers/*.pkg => installers"
        }
        artifacts(InstallerWindows) {
            buildRule = sameChainOrLastFinished()
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
