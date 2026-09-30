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
- De-branded: our name everywhere upstream hard-coded "VOICEVOX TTS Engine", and the black-yellow
  icon traced from upstream's launcher artwork with 音.
- **VOICEVOX CORE 0.17.0** — upstream's open pull request #19 folded in (VOICEVOX ONNX Runtime 1.23.2,
  models and runtime fetched at build time, initialisation off the main thread).
- **Every VOICEVOX voice.** The APK bundles **No.7** (`6.vvm`); all 27 models of the official
  voicevox_vvm 0.17.0 release are listed — 218 styles — and downloaded in-app on request, verified
  by SHA-256. **217 bundled samples** (1.8 MB of OGG/Opus, generated on the PC with the same
  release) play every voice before it is downloaded; singing voices are listed with sung samples.
- **The 白い熊 voice by default:** No.7（読み聞かせ）, speed 1.15 — the voice of
  shiroikuma-jisho-subtitles — with a *Reselect default* button.
- A multi-voice **system TTS service**: every installed style is an Android voice; speed, pitch,
  intonation, volume and pauses from the app's settings, optionally following the caller's rate.
- A **main screen** (upstream had none): engine status, preferred voice, a try-it box with a big
  speak button and timing, a jump to Android's TTS settings, the voice list, credits.
- **白い熊 音声 UI** — the settings page in the kxkb format, opened by a tap or long-press on the
  Settings cog: Export / Import first (settable backup directory, red until set, latest backup
  shown, Kōjiki-style panel with ArcaneChat pills and the closing chain), then Voice, Colours
  (RGBA sliders + one-click recent colours), Typography (imported fonts in their own glyphs),
  Borders & shape, Main screen, Settings page, Reset — every item live-previewed, black-yellow by
  default. Backups are `shiroikuma-onse_<yyyy-MM-dd_HH-mm-ss>.zip`.

- **保存復元 automation — sister-app contract v2.** `shiroikuma.onse.action.EXPORT_STATE` /
  `LIST_CATEGORIES` / `CANCEL_EXPORT` on an exported receiver, the export on a foreground service
  (guarded starts, keyed `ERROR:no-storage-access` / `no-directory` / `no-foreground-start`,
  real-count progress, one terminal reply); the data door `shiroikuma.onse.automation` for
  白い熊 応用管理 and 自由作業盤 (exact name, uid and pinned certificate; descriptor streaming with a
  heartbeat; spooled, durable import); the gate ON with the token opt-in — its three rows in the
  Export / Import section. Voices a restore lists are re-downloaded when the app next opens.

- **The render service** for sister apps (`docs/sister-app-contract-onse-render.md`):
  `shiroikuma.onse.action.RENDER` takes a batch of sentences and writes one OGG/Opus file per
  sentence at the exact path the caller names (device Opus encoder + OGG muxer, `.part` then
  rename, so a re-render replaces atomically); voice, speed, pitch, intonation, volume, pauses,
  bitrate and format per request; a reply per item (duration or error) and one `done`;
  `CANCEL_RENDER`; requests queue on a foreground service.
- **All-files access is checked on every entry** into the app and requested with an explanation.

- **Integration with 言語島:** `shiroikuma.onse.action.PING` (answers `event=pong` with version,
  installed styles, storage and battery-exemption state), an invisible exported
  `WarmActivity` to wake the process, and a **battery-optimisation check on every entry** —
  without the exemption EMUI refuses the render service's start from the background (measured on
  the Mate XT); the dialog requests it and names the Huawei App-launch settings.

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
