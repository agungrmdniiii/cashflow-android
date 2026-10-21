# In-app APK Updates Implementation Plan

> **For agentic workers:** Use executing-plans inline; no delegation is required.

**Goal:** Publish Duit Aing 1.0.0 with manual in-app update checks, professional release notes, download progress, verified APKs, and Android installation confirmation.

**Architecture:** A small metadata parser and Android ViewModel fetch a public GitHub Releases update.json over HTTPS, stream APKs into private cache, validate hash/package/version/signing certificate, and hand verified APKs to the Android installer. An About section uses the current Compose theme. GitHub Actions builds the same optimized debug distribution on main pushes using the existing signing key supplied as a repository secret.

**Tech Stack:** Kotlin, Compose Material 3, coroutines, HttpURLConnection, FileProvider, Python standard library, GitHub Actions. No new app dependencies.

**Spec:** User-approved discussion in this chat: download inside app without opening GitHub, describe actual changes professionally, version name 1.0.0.

## Constraints
- Preserve existing signing identity and financial data. Version code must exceed installed 110; local baseline 111.
- Update installation always uses Android user confirmation. No silent/background installation.
- Direct APK updates only in the APK test distribution; keep installer permission out of release/Play builds.
- No tokens or private signing keys in source, output, or APK. CI must fail rather than generate a replacement signing key.
- Update network work must not block rendering or ledger operations. No automatic download.
- Keep debug output at app/build/outputs/apk/debug/app-debug.apk; install -r on the authorized phone and emulator.

## Tasks
- [x] Add version.properties and replace hard-coded UI/diagnostic/backup version labels with build metadata. Author professional release notes.
- [x] Test metadata validation and version comparison, including unsafe hosts, malformed hashes, empty notes, excessive APK sizes, and unsupported Android versions.
- [x] Implement HTTPS metadata checks and cancellable streaming downloads with byte/hash limits. Verify package, internal version, and installed signing certificate before exposing installation.
- [x] Add About update dialog: installed/latest versions, release notes, progress, retry, cancel, ready/install, unavailable/no-network/no-release states; recover from denied install permission.
- [x] Prepare automatic signed CI publishing with monotonic version codes and matching changelog metadata. Document the signing secret and activation steps.
- [x] Verify unit tests, isolated Android checks for certificate/package/metadata and installer intent, and visual states. Build and install latest APK without deleting data.
- [x] Report live GitHub activation status separately from local feature completion; never claim an unpublished update feed is live.

## Review focus
- A bad or interrupted download must never become installable.
- Existing testers must not be forced to uninstall because of version/signing changes.
- Latest metadata may be absent, outdated, invalid, or incompatible; show a truthful recovery message.
- Opening/closing settings while downloading must preserve progress and allow cancellation.
- CI signing credentials must never be printed, committed, or replaced with a generated key.

## Verification ledger

- Version 1.0.0 / code 111 built; APK update installed over code 110 on POCO F6 and emulator.
- 250 unit tests pass; five Android update/ledger checks pass. Ledger fingerprint matches pre-feature baseline.
- Full Android lint passes; installer permission present only in debug manifest and absent from release manifest.
- Public signing fingerprint verified against the original debug keystore. User explicitly authorized main publication and encrypted repository signing-secret configuration; secret configured without exposing key/token values.
- Automatic publication succeeded: https://github.com/agungrmdniiii/cashflow-android/actions/runs/39669652293.
- Published version 1.0.0 / code 116: https://github.com/agungrmdniiii/cashflow-android/releases/tag/v1.0.0-build.116. Public update.json and APK are available.
- Real emulator update flow passed twice (111 to 114, then 114 to 116): check, professional notes, in-app download, verification, Android source permission, native installation. Final package passed the Play Protect scan requested by Android.
- Final five Android update/ledger tests pass against the published code 116 APK. Financial-table fingerprint remains 2cda2eae55dd0e5bf8a3e9e1c808e6c1bf9ecac163e6b7707f4970e0a22b309a.
- POCO F6 and emulator both have code 116; installed APK SHA-256 matches the published artifact and the single debug output: 75a0332a72d2b24d29d0b3c91b207d821b7d0d9ec288ee1583c5c714ca7b95f8.
- Signing and draft asset lookup failures in the initial workflow were corrected. CI now uses an explicit existing-keystore path and resolves draft assets through gh release view. Final workflow completed successfully.
