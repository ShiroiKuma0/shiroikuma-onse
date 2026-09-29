# Changelog

This file carries both histories: 白い熊 音声's releases first, newest first, then upstream's.
Upstream (0266st/VOICEVOX_TTS_Engine_For_Android) publishes no changelog of its own, so its section
below is written from its tags, merged pull requests and commit history.

## 白い熊 音声 — unreleased

Built on upstream `master` at `bfbe4f2` (2026-01-25).

- Fork set up as **白い熊 音声** (`shiroikuma.onse`), installable side-by-side with upstream.
- Fork build layer (`shiroikuma/fork.gradle`): the `onse` flavour, arm64-v8a only, our own signing
  key, versions `<upstream>+NNN` with versionCode `<upstream> × 10000 + NNN`, and the `buildFork` task
  that writes `~/tmp/shiroikuma-onse_<version>_arm64-v8a.apk`.

# Upstream — VOICEVOX TTS Engine for Android

## Unreleased on `master` (2025-03-17 – 2026-01-25)

- **VOICEVOX CORE resolved at build time** (#2): a `downloadVoicevox` Gradle task fetches the core's
  Java packages from the VOICEVOX release into the local Maven repository, instead of committed AARs.
- **Back to the 0.15 core API while keeping the Maven dependencies** (#12).
- **Unneeded permissions removed** (#16).
- **Continuous integration:** GitHub Actions builds every pull request (#1, #3, #4, #5) and uploads
  the debug APK as an artifact (#17).
- README: development paused (2025-03-18), then resumed (#9).
- A "variant" shown as `null` when empty.

## 1.0.0 — 2025-03-17

- First release: an Android system text-to-speech engine that replaces the device's reading voice
  with VOICEVOX, built on the VOICEVOX CORE Android AAR, ONNX Runtime and OpenJTalk (dictionary
  bundled). Default voice 冥鳴ひまり (`1.vvm`). MIT licence.
