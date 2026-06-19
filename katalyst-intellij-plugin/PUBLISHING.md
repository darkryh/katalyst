# Publishing the Katalyst plugin to the JetBrains Marketplace

The plugin installs into IntelliJ IDEA / Android Studio from the JetBrains Marketplace. Releases are
**automated via GitHub Actions** and signed; you normally never run Gradle by hand.

## How a release works

- Releases ship **only** on an explicit plugin release, never on unrelated framework commits:
  push a tag `plugin-v<version>` (e.g. `plugin-v0.1.0`). The tag drives the published version.
- `.github/workflows/release-plugin.yml` then builds, signs (with the secrets below) and publishes.
- `.github/workflows/plugin-ci.yml` separately validates the plugin on every push/PR **that touches
  `katalyst-intellij-plugin/**` or `katalyst-conventions/**`**, so a plugin change is always built
  before it can be released, and unrelated commits don't pay the IntelliJ-SDK download cost.

```bash
git tag plugin-v0.1.0
git push origin plugin-v0.1.0     # -> triggers the release workflow
```
You can also run the **Release IntelliJ plugin** workflow manually (workflow_dispatch) with an
explicit version + channel.

## Repository secrets

| Secret | Status | What it is |
|--------|--------|------------|
| `CERTIFICATE_CHAIN` | ✅ already set | PEM signing certificate chain |
| `PRIVATE_KEY` | ✅ already set | PEM (unencrypted) signing private key |
| `PUBLISH_TOKEN` | ⬜ **you must add** | JetBrains Marketplace permanent token |

The signing key is unencrypted, so there is **no password secret** — that avoids a flaky OpenSSL
passphrase path and is fine because GitHub encrypts secrets at rest and only exposes them to the
workflow. To rotate the key, regenerate and re-set the two secrets:
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out private.pem
openssl req -new -x509 -key private.pem -days 3650 -subj "/CN=darkryh" -out chain.crt
gh secret set CERTIFICATE_CHAIN < chain.crt
gh secret set PRIVATE_KEY < private.pem
rm private.pem chain.crt
```

### Add the publish token (one time)
```bash
# Token from https://plugins.jetbrains.com/author/me/tokens
gh secret set PUBLISH_TOKEN --repo darkryh/katalyst
```

## First release is manual (creates the Marketplace listing)

The very first version must be uploaded through the web UI to create the listing
(https://plugins.jetbrains.com/plugin/add) — pick the `.zip`, a license, and source URL
`https://github.com/darkryh/katalyst`. It then goes through JetBrains moderation (a few business
days). After the listing exists, every later version ships via the `plugin-v*` tag.

Build the ZIP for that first upload locally:
```bash
cd katalyst-intellij-plugin
./gradlew buildPlugin     # -> build/distributions/katalyst-intellij-plugin-<version>.zip
./gradlew runIde          # optional: try it in a sandbox IDE
```

## Before each release
- Update `<change-notes>` in `src/main/resources/META-INF/plugin.xml`.
- Confirm the `<vendor email="…">` in `plugin.xml` is the email you want shown publicly.
- Tag `plugin-v<version>` — the version is taken from the tag, so `build.gradle.kts` needs no edit.
