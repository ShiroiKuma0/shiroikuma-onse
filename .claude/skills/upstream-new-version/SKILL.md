---
name: upstream-new-version
description: Sync the shiroikuma-onse fork (白い熊 音声) onto new upstream work in 0266st/VOICEVOX_TTS_Engine_For_Android — check upstream `master` for new commits, present 白い熊 a proceed-gated, tabular, descriptive summary of what the new upstream version introduces, then (only on an explicit go-ahead) reset `master` to the new upstream head, rebase the `custom` patch stack onto it, merge the changelog, reset the build counter if upstream's version moved, and build. Use when 白い熊 runs /upstream-new-version, says a new upstream VOICEVOX TTS version is out, or asks to check for a new version, update/sync to upstream, or rebase custom onto upstream.
---

# Sync shiroikuma-onse onto new upstream work

`master` mirrors **`upstream/master`**; `custom` carries our patches and is rebased onto it. Every
concrete fact (identity, keystore, version scheme, the build) lives in the **`build-apk`** skill —
read it first.

> **Never `git push` or `git commit` unprompted, and never `adb install`.** The rebase and build
> happen on the local tree; everything is re-runnable (`git rebase --abort`) until 白い熊 says
> **"Push"**.

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `master` | Upstream's `master` head. No fork work here. | `git checkout -B master upstream/master` |
| `custom` | Our patches; the working branch and the GitHub default branch. | rebased onto `master` |

`origin` = `git@github.com:ShiroiKuma0/shiroikuma-onse` (push). `upstream` =
`https://github.com/0266st/VOICEVOX_TTS_Engine_For_Android` (fetch only, push URL `DISABLED`).

**Upstream tracking: the `master` branch, not tags** (白い熊, 2026-09-29). Upstream tagged once
(`v1.0.0`, 2025-03-17) and kept committing to `master` without bumping `versionName` `1.0`. If it
starts tagging releases again, still base on `master` but name the tag in the Step 2 table.

## Step 0 — Preconditions

- cwd `~/git/shiroikuma-onse`, on `custom`, working tree clean (`git status --short` empty;
  `keystore.properties` is gitignored).
- git / `gh` / `gradlew` **unsandboxed** (`dangerouslyDisableSandbox: true`).

## Step 1 — Is there new upstream work?

```bash
git fetch upstream --tags --force
OLD=$(git rev-parse master); NEW=$(git rev-parse upstream/master)
git log --oneline $OLD..$NEW | wc -l                  # 0 → report "already current" and stop
git tag -l --sort=-creatordate | head -5; gh release list -R 0266st/VOICEVOX_TTS_Engine_For_Android -L 5
git show $NEW:app/build.gradle.kts | grep -E 'version(Name|Code) ='      # did upstream's pair move?
git show $NEW:gradle/libs.versions.toml | grep -E '^(voicevox|onnxruntime|agp|kotlin) ='
gh pr list -R 0266st/VOICEVOX_TTS_Engine_For_Android --state merged -L 20 \
  --json number,title,mergedAt,body                    # what the merged PRs say they do
```

Read the commit **bodies** (`git log --format='%h %cs %s%n%b' $OLD..$NEW`), not only the subjects,
and the merged PR descriptions. Then get the conflict set before anything moves:

```bash
git diff --name-only $OLD..$NEW > /tmp/onse-upstream-touched.txt
git diff --name-only master..custom | grep -Fxf /tmp/onse-upstream-touched.txt
git diff --stat $OLD..$NEW -- app/src/main/res/raw gradle/libs.versions.toml app/build.gradle.kts
```

Also check whether upstream merged something our patch stack carries a copy of (PR #19,
`0266st/core-0.17`): after the rebase those commits should drop out as empty.

## Step 2 — ⛔ PROCEED GATE: the new-features table (MANDATORY, before any branch moves)

**白い熊's standing request: before rebasing, give a tabular, descriptive summary of what the new
upstream version introduces, and wait for an explicit go-ahead.** No `master` move, no rebase, no
build until they say proceed. Never skip it, never fold it into the rebase turn.

Describe what each change **does**, in plain language — not a commit dump. Cover **every** commit
between our base and the new head. Group features first, then fixes; fold noise (CI, IDE files,
dependency bumps) into one row each.

| Date / PR | Area | Change | What it does for us | Touches our patches? |
| --- | --- | --- | --- | --- |
| e.g. 2026-10-02 #19 | Engine | VOICEVOX CORE 0.15 → 0.17, onnxruntime 1.23.2 | Newer core; our copy of #19 drops out | **Yes** — we carry #19 |
| e.g. 2026-10-05 #21 | Synthesis | Streaming synthesis | First audio sooner in system TTS | **Yes** — the render service shares the synthesizer |
| e.g. 2026-10-05 #22 | Voices | Voice picker | Several voices selectable | **Yes** — our No.7 default, the UI page |
| e.g. — | CI | Workflow updates | — | No |

Flag in the last column anything that:

- touches a **file our layer patches** (the inventory in Step 6) — likely conflicts;
- changes **synthesis / the TTS service** (`VoicevoxTTSService`, `VoicevoxTextToSpeechServiceImplement`,
  the engine wrapper), the **model or dictionary resources** (`res/raw`), or the **core / onnxruntime
  versions** — the render contract and the No.7 model hang off these;
- adds **branding or links** (About, Help, README, labels) our de-branding must cover;
- adds **permissions, network access or telemetry**;
- moves **upstream's `versionName` / `versionCode`** (the counter resets);
- is a genuinely useful fix for 白い熊.

Close with the move (`OLD` → `NEW` short hashes and dates), the commit count, the conflict set and
whether upstream's version pair moved — then ask **"Proceed with the rebase to `NEW`?"** and **STOP**.
Only an explicit "proceed" / "go" / "yes" continues. If 白い熊 declines, nothing has been touched.

## Step 3 — Advance `master`

```bash
git branch -f custom-pre-$(git rev-parse --short upstream/master) custom   # local safety ref; /publish-version deletes it
git checkout master
git reset --hard upstream/master        # master carries no fork work; it IS upstream's head
```

## Step 4 — Rebase `custom`

```bash
git checkout custom
git rebase master
```

Resolve so **all** our customizations survive — reconcile, don't drop; if upstream restructured a
file we patch, port our change onto the new structure.

- **`app/build.gradle.kts`**: our only line is the last one,
  `apply(from = "$rootDir/shiroikuma/fork.gradle")`. Keep upstream's new version literals exactly.
- **`README.md`**: ours replaces upstream's — a conflict here is expected: keep ours, and carry any
  new credit / licence line upstream added into our credits.
- **`CHANGELOG.md`**: upstream has none (as of 2026-09-29); if it adds one, keep its text
  byte-for-byte below our block.

**"Not huge" — resolve in place**, `git add`, `git rebase --continue`: context shifts, a commit going
empty because upstream did the same (e.g. PR #19 merged upstream).

**"Significant" — STOP and plan with 白い熊**: upstream refactored something a patch depends on (the
synthesizer / TTS service, the model loading, the build files), many commits conflict, or a
**semantic** conflict (hunks merge but behaviour changed). Gather `git status`, the conflicted hunks
and what upstream did to that file, say which of **our** commits conflicts and why, and present
options — resolve together, re-derive the commit, defer it, or `git rebase --abort`.

## Step 4b — Re-check the de-branding

```bash
grep -rn -i "0266st\|ztssst\|VOICEVOX TTS Engine" app/src/main --include=*.kt --include=*.xml
```

A new hit outside the code namespace (`dev.ztssst.voicevox_tts` package names are expected) is new
upstream branding — route it through our flavour resources / constants like the existing ones.

## Step 5 — The build counter

If upstream's `versionName` / `versionCode` moved, set **`BUILD_NUMBER=1`** in
`shiroikuma/fork.properties`. If they did not move (the usual case — upstream sits at `1.0` / `1`),
**leave the counter alone** so versionCode stays monotonic. Never touch `LAST_BUILT_VERSION_CODE`.

## Step 6 — Verify our customizations survived

| What | Expected | Where |
| --- | --- | --- |
| Fork hook | `apply(from = "$rootDir/shiroikuma/fork.gradle")` as the last line | `app/build.gradle.kts` |
| Flavour, signing, version, `buildFork` | `onse` flavour: `shiroikuma.onse`, arm64-v8a, `signingConfigs.onse` | `shiroikuma/fork.gradle` |
| Counter | per Step 5 | `shiroikuma/fork.properties` |
| Flavour source set | label, icon, de-branding overrides, our code | `app/src/onse/` |
| Gitignore block | `keystore.properties`, `*.jks`, `/.scratch/` | `.gitignore` |
| Our guide, plan + skills | present | `CLAUDE.md`, `docs/PLAN.md`, `.claude/skills/` |
| Voices, main screen, UI page | catalogue + samples, engine, TTS service, launcher, 白い熊 音声 UI | `app/src/onse/assets/voices/`, `app/src/onse/java/shiroikuma/onse/` |
| Automation contract v2 | receiver + export service, data door, gate rows in Export / Import | `app/src/onse/java/shiroikuma/onse/automation/`, flavour manifest |
| Feature patches | every shipped customization (keep this table growing as they land) | their files |

Then confirm the build script still evaluates:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk \
  bash ./gradlew buildFork --dry-run --console=plain < /dev/null
```

## Step 7 — Merge the changelog

**白い熊's standing request: every release publishes a merged changelog.** At the top of
`CHANGELOG.md`, our block records — newest first — each fork release as
`## 白い熊 音声 <version> — <YYYY-MM-DD>`, naming the upstream commit it is built on. For this sync,
extend the synthesised **Upstream** section with what the new commits brought (from Step 2's table):
upstream has no changelog of its own. If upstream ever adds a `CHANGELOG.md`, its text goes below our
block verbatim instead (`git diff master -- CHANGELOG.md | grep -c '^-[^-]'` → `0`). The global
`/publish-version` publishes the merged file with the release.

## Step 8 — Build

Build via the **build-apk** skill (`bash ./gradlew buildFork`) and deliver via `/after-build`. If the
core version moved, run `:app:downloadVoicevox` first. A build failure on the rebase result is a
significant conflict — diagnose and replan with 白い熊, don't patch blindly.

## Step 9 — Stop, then push only on "Push"

Let 白い熊 test. Only on their explicit **"Push"**:

```bash
git push --force-with-lease origin master      # master moved to the new upstream head
git push --force-with-lease origin custom      # history rewritten by the rebase
```

`--force-with-lease`, never bare `--force`.

## Hard rules

- Never skip the Step 2 proceed gate.
- Never `adb install` / `adb uninstall`; never commit / push unprompted.
- Never rename the `dev.ztssst.voicevox_tts` namespace — only `applicationId` differs.
- Never hand-edit upstream's `versionName` / `versionCode`.
- No Claude attribution in commits (see `CLAUDE.md`).
