# komet-ws — Project Notes

<!-- This file is for hand-authored, project-specific information.
     It is created by ws:init but never overwritten.
     Commit this file to git. -->

## Architecture

## Key Classes

## Testing Notes

## TeamCity Integration

The `.teamcity/` directory contains TeamCity Kotlin DSL versioned settings:

- **`settings.kts`** — pipeline configuration (4-stage: deploy, macOS installer, Windows installer, publish release)
- **`pom.xml`** — required by TC server to compile the DSL (resolves TC DSL dependencies)

### Critical constraint: Maven 4 reactor exclusion

Maven 4 auto-discovers `pom.xml` files in subdirectories and adds them to the reactor.
`.teamcity/pom.xml` uses TC-specific SNAPSHOT dependencies that break the workspace build.

**The fix:** `.mvn/maven.config` contains `-pl !.teamcity` to exclude it from the reactor.

**Do NOT:**
- Remove the `-pl !.teamcity` line from `.mvn/maven.config`
- Move `.teamcity/pom.xml` into the workspace reactor
- Add `.teamcity` as a `<subproject>` in the root POM

## macOS Code Signing

The `komet-desktop` module signs native libraries and installer packages using
Apple's `codesign` tool with a Developer ID Application certificate.

### Developer Machine Setup

1. Install your Developer ID Application certificate in the login keychain
2. Store the keychain password securely (one-time):
   ```bash
   security add-generic-password -a "$USER" -s codesign-keychain-password -w
   ```
3. Add to `~/.zprofile` (retrieves from Keychain — no plaintext password in dotfiles):
   ```bash
   export CODESIGN_KEYCHAIN_PASSWORD="$(security find-generic-password -a "$USER" -s codesign-keychain-password -w 2>/dev/null || true)"
   ```
3. The `ike:codesign-natives` and `ike:codesign-pkg` goals will automatically
   unlock the keychain before signing when this variable is set

Without `CODESIGN_KEYCHAIN_PASSWORD`, signing still works but prompts
interactively for keychain access (blocks CI and headless builds).

### Signing Identity

Configured in `komet-desktop/pom.xml`:
```xml
<codesign.identity>Developer ID Application: Keith Eugene Campbell (8WUE5BZ4BP)</codesign.identity>
```

### Skipping Signing

For development builds where signing is unnecessary:
```bash
mvn clean verify -Dcodesign.skip=true -Dcodesign.pkg.skip=true
```

### TeamCity CI

The `CODESIGN_KEYCHAIN_PASSWORD` must be configured as a secret parameter
(`env.CODESIGN_KEYCHAIN_PASSWORD`) in the TeamCity project settings. The
macOS build agent must have the Developer ID certificate installed in its
login keychain.
