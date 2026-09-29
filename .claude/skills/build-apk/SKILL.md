---
name: build-apk
description: Build the signed release APK of shiroikuma-onse (白い熊 音声 — 白い熊's fork of 0266st/VOICEVOX_TTS_Engine_For_Android, the VOICEVOX system TTS engine and render service, app id shiroikuma.onse) with the `buildFork` Gradle task, and deliver it automatically via the global /after-build skill (adb push if a phone is connected, else scp to skhw — no prompt). Always build without asking permission. Use whenever 白い熊 mentions 音声, onse, VOICEVOX on the phone, asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the 白い熊 音声 release APK and deliver it

> **Never ask whether to build — just build.** When this skill applies (白い熊 asked to build, or a
> change is finished), run the build immediately. There is **no** transfer question either: after a
> successful build, deliver via the global **`/after-build`** skill — no prompts at all.

> **The push destination is ALWAYS `/sdcard/tmp/`.** Never `adb install` / `pm install` /
> `adb uninstall` — 白い熊 installs the APK from the phone's file manager.

> **Never `git commit` or `git push` on your own.** Building does not include committing. Only on
> 白い熊's explicit **"Push"** do you commit and `git push origin custom` ("Push" is unrelated to
> `adb push`).

## Project identity

| Item | Value |
|------|-------|
| Upstream repo | `0266st/VOICEVOX_TTS_Engine_For_Android` (remote `upstream`, HTTPS, **fetch only** — push URL `DISABLED`) |
| Fork repo | `git@github.com:ShiroiKuma0/shiroikuma-onse.git` (remote `origin`, SSH — push here) |
| Local working tree | `~/git/shiroikuma-onse` |
| Mirror branch | `master` — reset to **`upstream/master`** (we track the branch, not tags), never carries our changes |
| Custom branch | `custom` — all our commits, rebased onto `master`; the GitHub default branch |
| applicationId | `shiroikuma.onse` |
| App label | `白い熊 音声` |
| Settings page of our changes | `白い熊 音声 UI` (long-press on the main screen's Settings cog) |
| Code namespace (**UNCHANGED**) | `dev.ztssst.voicevox_tts` — never rename |
| Product flavour | `onse` (defined in `shiroikuma/fork.gradle`) |
| Target ABI | `arm64-v8a` only (`ndk.abiFilters`) → one APK |
| Gradle task | `bash ./gradlew buildFork` (resolves to `:app:buildFork`) |
| Built APK dir | `app/build/outputs/apk/onse/release/` |
| Delivered APK | `~/tmp/shiroikuma-onse_<versionName>_arm64-v8a.apk` → `/sdcard/tmp/` |
| Keystore | `~/.android-keystores/shiroikuma-onse.jks`, alias `onse` |
| Build JDK | OpenJDK 21 at `/usr/lib/jvm/java-21-openjdk-amd64` |
| Android SDK | `~/android-sdk` |
| Gradle / AGP | upstream's wrapper (8.11.1 as of 2026-09-29) / AGP from `gradle/libs.versions.toml`; `app/build.gradle.kts` is Kotlin DSL, our `fork.gradle` is Groovy |

## Build environment (this machine)

The default `java` is JDK 11, and the Android SDK is not on a default env var. Export both in
**every** invocation, and run every build, git and keystore command with
`dangerouslyDisableSandbox: true`:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/shiroikuma/android-sdk
```

Upstream's `gradlew` is committed **without the executable bit** — always `bash ./gradlew …`; do not
`chmod` it (a mode change on an upstream file is a needless rebase conflict).

## Steps

1. **VOICEVOX CORE in the local Maven repo?** Upstream resolves `jp.hiroshiba.voicevoxcore` from
   `mavenLocal()`, filled by its `downloadVoicevox` task. On a fresh machine, or after the core
   version in `gradle/libs.versions.toml` moves, run it once:

   ```bash
   bash ./gradlew :app:downloadVoicevox --console=plain < /dev/null
   ```

2. **Note the version you are about to produce:**

   ```bash
   grep -E '^(BUILD_NUMBER|LAST_BUILT_VERSION_CODE)=' shiroikuma/fork.properties
   grep -E 'version(Name|Code) =' app/build.gradle.kts
   ```

   The APK will be `shiroikuma-onse_<versionName>+<BUILD_NUMBER padded to 3>_arm64-v8a.apk` with the
   counter **before** the build. Read the printed `>>>` lines rather than reconstructing it.

3. **Build** (release, signed):

   ```bash
   bash ./gradlew buildFork --console=plain < /dev/null
   ```

   - Runs `assembleOnseRelease`, copies the signed APK to `~/tmp/<apk name>` (refusing to overwrite
     an existing file), bumps `BUILD_NUMBER` and records `LAST_BUILT_VERSION_CODE`.
   - Prints `>>> <path>`, `>>> versionCode <n>`, `>>> BUILD_NUMBER bumped to <n>` in cyan; confirm
     `BUILD SUCCESSFUL`.
   - A cold build (fresh Gradle cache) can exceed the foreground timeout — use `run_in_background`.
   - **Fast iteration:** `bash ./gradlew :app:assembleOnseDebug`. The shippable build is `buildFork`.

4. **Deliver via `/after-build`** — every successful build, no asking.

## Signing

`shiroikuma/fork.gradle` defines `signingConfigs.onse` from the gitignored **`keystore.properties`**
at the repo root (`storeFile` / `storePassword` / `keyAlias` / `keyPassword`) and sets it on the
release build type.

- Keystore `~/.android-keystores/shiroikuma-onse.jks`, alias `onse` — PKCS12, RSA-4096,
  SHA384withRSA, 10000 days, DN `CN=白い熊 音声, O=ShiroiKuma0`, created 2026-09-29. Certificate
  SHA-256 `BD:11:C8:79:BF:3A:9D:1A:20:2C:4A:5C:89:84:85:FA:E7:D0:8D:E3:0B:B1:1A:54:C9:3C:D1:2C:24:42:A6:4A`.
- Password: `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`; the `.jks` is mirrored to
  `~/〇/[666] 私資料/[666][27] 暗号/android-keystores/`.
- Missing `keystore.properties` → the configuration prints `shiroikuma: no keystore.properties …`
  and the release is unsigned. Restore it rather than shipping anything else:

  ```bash
  cat > keystore.properties <<EOF
  storeFile=$HOME/.android-keystores/shiroikuma-onse.jks
  storePassword=<from the vault>
  keyAlias=onse
  keyPassword=<same>
  EOF
  chmod 600 keystore.properties
  ```

## Versioning (how the numbers are formed)

All of it is in **`shiroikuma/fork.gradle`**, applied as the last line of `app/build.gradle.kts`, so
upstream's `defaultConfig` is already evaluated when it reads it.

- Upstream's pair is `versionName` / `versionCode` in `app/build.gradle.kts` (`1.0` / `1` as of
  2026-09-29). Never hand-edit it.
- `BUILD_NUMBER` in **`shiroikuma/fork.properties`** is our per-build increment.
- `versionName = "<upstream versionName>+<BUILD_NUMBER padded to 3>"` → `1.0+001`.
- `versionCode = <upstream versionCode> * 10000 + BUILD_NUMBER` → `10001`.
- `BUILD_NUMBER` **resets to `1` only when upstream's pair changes** (`upstream-new-version`). Because
  we track upstream's branch, a sync that brings new commits but the same `1.0` / `1` keeps counting.
  `LAST_BUILT_VERSION_CODE` is the floor: `buildFork` fails rather than build at or below it.

## Related skills

- **`upstream-new-version`** — proceed-gated sync onto new upstream `master` commits.
- Global **`/after-build`** (deliver), **`/publish-version`** (GitHub release with the merged changelog).

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line
of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
