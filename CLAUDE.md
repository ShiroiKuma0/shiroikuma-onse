# CLAUDE.md — shiroikuma-onse

**白い熊 音声** — 白い熊's fork of [VOICEVOX TTS Engine for Android](https://github.com/0266st/VOICEVOX_TTS_Engine_For_Android)
(MIT), an Android system text-to-speech engine built on VOICEVOX CORE, ONNX Runtime and OpenJTalk.
Package **`shiroikuma.onse`**, installable side-by-side with upstream (`dev.ztssst.voicevox_tts`).

Why this fork exists: to give the phone the same VOICEVOX voice the PC uses in
`~/git/shiroikuma-jisho-subtitles` (**No.7 / 読み聞かせ, style id 31**, speedScale 1.15), and to make it
a generic render service other sister apps drive by intent — first of all the **言語島 (gengoshima)
Japanese Language Islands suite** in `~/git/shiroikuma-jiyusagyoban` (project 日本語 [227]), which asks
this app to render each sentence to OGG/Opus at a path it names. It stays a system TTS voice too.

The agreed design and the backlog live in **`docs/PLAN.md`** — read it first.

## Read this first

- **`docs/PLAN.md`** — scope, the render contract, the UI-page requirements, the step order.
- **`.claude/skills/build-apk/SKILL.md`** — identity, the build, signing, versioning.
- **`.claude/skills/upstream-new-version/SKILL.md`** — the proceed-gated upstream sync.

## Branch & remote model (same as the sister forks)

| Branch | Role | Update mode |
| --- | --- | --- |
| `master` | Mirrors **`upstream/master`** (see below). No fork work here. | reset to each new upstream head |
| `custom` | All our work, rebased onto `master`; the GitHub default branch. | rebased each sync |

- `origin` = `git@github.com:ShiroiKuma0/shiroikuma-onse.git` (ssh, push here).
- `upstream` = `https://github.com/0266st/VOICEVOX_TTS_Engine_For_Android.git` (https, **fetch only** —
  push URL `DISABLED`).
- **We track upstream's `master` branch, not its tags** (decided by 白い熊 2026-09-29): the only tag,
  `v1.0.0`, is from 2025-03-17 and ~35 commits behind; upstream's `versionName` stayed `1.0`.
- **Never rename the code namespace** `dev.ztssst.voicevox_tts` — only the installed `applicationId`
  differs. Renaming would make every rebase a mass-conflict.

## Identity

| What | Value | Where |
| --- | --- | --- |
| applicationId | `shiroikuma.onse` | `shiroikuma/fork.gradle` → flavour `onse` |
| App label | `白い熊 音声` | flavour resources in `app/src/onse/res` (de-branding step) |
| Our settings page | **`白い熊 音声 UI`** — every configurable item of the fork; opened by a **long-press on the Settings cog** of the main screen | to be built (see `docs/PLAN.md`) |
| Flavour | `onse` — arm64-v8a only | `shiroikuma/fork.gradle` |
| Launcher icon | our black-yellow traced icon | `shiroikuma/icon/` (to be made) |
| Keystore | `~/.android-keystores/shiroikuma-onse.jks`, alias `onse` | `keystore.properties` (gitignored) |

## Build (summary — details in `build-apk`)

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk
bash ./gradlew buildFork < /dev/null     # signed APK → ~/tmp/shiroikuma-onse_<version>_arm64-v8a.apk, bumps the counter
```

- Upstream's `gradlew` is committed without the executable bit — call it through `bash`.
- **Version:** `versionName = "<upstream versionName>+<BUILD_NUMBER padded to 3>"` (e.g. `1.0+001`),
  `versionCode = <upstream versionCode> * 10000 + BUILD_NUMBER` (`1` → `10001`). Upstream's pair is
  `versionName` / `versionCode` in `app/build.gradle.kts`, never hand-edited. `BUILD_NUMBER` lives in
  **`shiroikuma/fork.properties`**, is bumped by every `buildFork`, and resets to `1` only when
  upstream's pair changes. Every build gets a new number; a build is never overwritten.
- APK: `~/tmp/shiroikuma-onse_<versionName>_arm64-v8a.apk`; delivered via the global `/after-build`.

## The fork layer — where our changes live

Keep it a **small, legible layer** so rebases stay cheap:

- `shiroikuma/` — everything that is only ours: `fork.gradle` (flavour, signing, version,
  `buildFork`), `fork.properties` (counter), `icon/` (icon source + generators).
- `app/build.gradle.kts` — our only line is the last one:
  `apply(from = "$rootDir/shiroikuma/fork.gradle")`.
- `app/src/onse/` — the flavour source set: resources that override `main`'s by name (label, icon,
  de-branding) and our own code.

## Changelog

**Every release publishes a merged `CHANGELOG.md`** (白い熊's standing rule, global `/publish-version`):
our releases newest-first at the top as `## 白い熊 音声 <version> — <YYYY-MM-DD>`, each naming the
upstream commit it is built on. Upstream publishes **no `CHANGELOG.md`**, so the file carries a
synthesised *Upstream* section written from upstream's tags, merged PRs and commit history; if
upstream ever adds a `CHANGELOG.md`, its text is merged verbatim below our block instead.

## Working rules

- **No `Co-Authored-By: Claude` / "Generated with Claude" trailer** in commits, PR bodies or release
  notes — end the message at the last line of the body. (Global rule, `~/.claude/CLAUDE.md`.)
- **Never commit or push until 白い熊 says "Push".** "Push" = commit + `git push origin custom`
  (and `master` after an upstream sync).
- **Only signed builds leave the PC.** Never `adb install` / `adb uninstall`; 白い熊 installs from
  `/sdcard/tmp/`. Never delete an old build.
- git / `gh` / `gradlew` / `keytool` / `adb` run **unsandboxed** (`dangerouslyDisableSandbox: true`).
- Commit subjects: plain descriptive summary, no prefix.
