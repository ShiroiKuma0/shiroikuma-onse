# shiroikuma-onse — status and handover

Written 2026-10-02, when the 言語島 plan was complete and verified on the phone. Read this before
working on 音声; `CLAUDE.md` holds the repo rules, `.claude/skills/` the build and upstream-sync recipes.

## What 白い熊 音声 is

The VOICEVOX app of the 白い熊 family (`shiroikuma.onse`, fork of
`0266st/VOICEVOX_TTS_Engine_For_Android`, MIT). Four jobs:

1. **System TTS engine.** Every installed VOICEVOX style is an Android voice (`voice/OnseTtsService`).
2. **Render service for sister apps.** A batch of sentences in, one OGG/Opus file per sentence out,
   at exactly the paths the caller names, in Japanese (VOICEVOX) or English (Kokoro-82M).
   Contract: [`docs/sister-app-contract-onse-render.md`](sister-app-contract-onse-render.md).
3. **Voice library.** Every VOICEVOX voice can be heard (bundled samples) and downloaded in-app;
   English voices likewise.
4. **A 白い熊 house app.** Main screen, 白い熊 音声 UI page, Export / Import, 保存復元 automation.

It owns no naming or layout of anyone's files: callers decide every path.

## Its place in 言語島 (gengoshima)

言語島 is 白い熊's Japanese Language Islands suite (Mikel Hyperpolyglot's method). The suite-level
design record — architecture across the four apps, decisions, settings, file layout, contracts,
verification — is **`~/git/shiroikuma-jiyusagyoban/docs/gengoshima.md`** (the original plan file,
`~/.claude/plans/cosmic-strolling-bubble.md`, is outside every repo; that document supersedes it).

| App | Role in 言語島 |
| --- | --- |
| 白い熊 自由作業盤 (`shiroikuma-jiyusagyoban`) | the suite: sentence entry, Claude translation, island editor, the car player, statistics, the [227][01] settings, the 暗記 sync client |
| **白い熊 音声 (this repo)** | renders every sentence twice: Japanese No.7 / 読み聞かせ (style 31, speed 1.15) and English Kokoro **am_michael**, OGG/Opus 48 kbps |
| 白い熊 暗記 (`shiroikuma-anki`) | the `islands.list` / `islands.sync` door — contract `docs/sister-app-contract-anki-islands.md` in that repo; decks `言語島々::認識::<island>` / `言語島々::製作::<island>` |
| 白い熊の辞書 (`shiroikuma-jisho`) | `STUDY_AUDIO`: study one island's `000 島全体.ogg/.srt` with tap-to-look-up |

## Status — everything planned is done (2026-10-02)

All committed and pushed on `custom`; the phone runs `1.0+009`.

| Step | Where |
| --- | --- |
| Fork setup, keystore, skills, icon, de-branding | `shiroikuma/`, `.claude/skills/`, `app/src/onse/res` |
| VOICEVOX CORE 0.17 (upstream PR #19 folded in as one commit) | commit `4406201` |
| Every VOICEVOX voice: No.7 bundled, 27 models downloadable, 217 bundled samples | `voice/`, `assets/voices/` |
| Main screen + 白い熊 音声 UI (kxkb page format, Export / Import) | `MainScreen.kt`, `ui/` |
| 保存復元 automation, contract v2 | `automation/` |
| Render service + OGG/Opus encoding | `render/` |
| PING, `WarmActivity`, all-files + battery-exemption checks on entry | `render/RenderReceiver.kt`, `WarmActivity.kt`, `ui/StorageAccessGate.kt` |
| English: Kokoro-82M via sherpa-onnx, 28 voices, default am_michael | `english/English.kt` |
| 48 kbps samples and renders; clear "model not downloaded" warnings | — |

**Verified on the Mate XT:**
- **Japanese render:** 3 sentences gave valid mono OGG/Opus files. Warm, the first file arrives at
  about 1.5 s and 3 sentences take about 11 s; cold (with the battery exemption), about 13 s.
- **English render:** 3 sentences, am_michael, about 58 kbps. The first file took about 10 s, which
  includes loading the model; after that about 5–8 s per sentence (Kokoro runs at roughly half
  real-time speed on the phone).
- **言語島 end to end:**
  - English and Japanese audio for all 157 sentences.
  - The adoption of 白い熊's 150 hand-made cards: original note IDs kept, 46 misfiled cards fixed,
    review history present.
  - Add / edit (due date kept) / delete / rename / no orphan media — all passed (白い熊, 2026-10-02).
  - The Recall player with English audio — passed.

## Device facts that cost something to learn

- **EMUI drops a cold background start of 音声** — an `am broadcast` or a sister app's
  `startForegroundService` while 音声 is not running — unless 音声 is **exempt from battery
  optimisation**. On top of that, 白い熊 sets Settings → Battery → App launch → 白い熊 音声 to manual,
  with auto-launch, secondary launch and run in background all on. A temporary allowlist grant is
  not enough.
  - 音声 now checks and requests the exemption on every entry.
  - Callers wake 音声 first: the data door's `describe` call (about 70 ms, works cold) or
    `WarmActivity`, then `PING`, then `RENDER`.
- **The cold path cannot be tested from adb on this phone.** Warm the app, then test.
- **Opus at 20 kbps in "voip" mode audibly dulls speech.** 白い熊 heard it at once. Samples and
  renders are 48 kbps, general-audio mode.
- **Two ONNX runtimes coexist:**
  - VOICEVOX's is `libvoicevox_onnxruntime.so`, with versioned symbols.
  - sherpa-onnx comes as its **static-link** AAR, so ONNX Runtime sits inside `libsherpa-onnx-jni.so`
    and no `libonnxruntime.so` is packaged.
- **A missing model must be shouted.** Before the Kokoro model was downloaded, the UI read
  "Preferred: am_michael" as if English worked (白い熊, 2026-10-01). Every English surface now says
  in red that English does not work until the model is downloaded.

## How the voice assets are made (PC)

Inputs are cached in the gitignored `.scratch/`: the venv with `voicevox_core` 0.17 + `sherpa-onnx`,
the VOICEVOX ONNX Runtime, the `vvm-0.17.0/` models and the Kokoro model in `en/`. Outputs are committed.

- `.scratch/venv/bin/python shiroikuma/voices/gen-voices.py` writes `assets/voices/catalog.json` and
  `assets/voices/samples/<style>.ogg`. It reads the voicevox_vvm release in `RELEASE`. The 0.17
  speech styles are `streaming_talk`; in the singing model, `sing` is the teacher and `frame_decode`
  are the voices.
- `.scratch/venv/bin/python shiroikuma/voices/gen-english.py` writes `assets/voices/english.json`
  and `assets/voices/english/<voice>.ogg`. It uses Kokoro `kokoro-int8-multi-lang-v1_0`, whose
  speaker IDs are the documented v1.0 ones.
- `fetchOnseModel` puts `6.vvm` into the APK. `fetchSherpaOnnx` fetches the pinned sherpa-onnx AAR.
  Both are SHA-256 checked and run before every build.

## Contracts 音声 implements

| Contract | Document | Code |
| --- | --- | --- |
| Render (`RENDER`, `CANCEL_RENDER`, `PING`, `WarmActivity`) | `docs/sister-app-contract-onse-render.md` | `render/`, `WarmActivity.kt` |
| 保存復元 automation v2 (`EXPORT_STATE` …, data door `shiroikuma.onse.automation`) | `~/git/shiroikuma-jiyusagyoban/sister-app-contract-backup-automation-hand-off.md` | `automation/` |

## Open items

- **The 保存復元 roster:** jiyusagyoban has `shiroikuma.onse` in its `<queries>`, but there is no
  「保存 ⇨ shiroikuma.onse」 roster task yet, so 音声 is not in the batch backup. That is jiyusagyoban's work.
- **No GitHub release yet.** Every build so far was delivered by adb only. `/publish-version` would
  cut the first release with the merged `CHANGELOG.md`.
- **Upstream PR #19** is still open. When upstream merges it, `/upstream-new-version` should see
  our fold-in commit go empty.
- **Possible later work (not asked for):** offer English through the system TTS service; expose
  `createAudioQueryFromKana` for accent-level control.

## The 白い熊 音声 UI page — 白い熊's requirements (2026-09-29)

- Name **白い熊 音声 UI**. A **tap or long-press on the Settings cog** of the main screen opens it
  (白い熊 later asked for tap = long-press).
- It holds as configurable items **all our changes and modifiable configs**.
- **Layout, as in the sister repos:**
  - logical sections, **each category a bold, big, underlined heading** (the underline only as wide
    as the text);
  - items **significantly indented**, each sub-level further indented;
  - thin spacers between sections, **tight lines, no big padding**; padding only between top-level
    groups;
  - the same headings, sizes and separators as the **kxkb UI page**.
- **Black-yellow default** for almost everything (black background, yellow text, yellow border), all
  of it settable and grouped logically.
- **Colour selectors:** four RGBA sliders with a preview, and above them one-click boxes pre-filled
  with previously selected colours.
- **External fonts** as in the sister repos; font choices render **in their own glyphs**.
- **Sizes:** every size selector is a slider; borders and the like go down to 0. **Everything
  previews live.**
- **Export / Import is the first section**, as in the Kōjiki UI page:
  - a settable directory, queried on opening the page for the latest export;
  - backup names `shiroikuma-onse_<yyyy-MM-dd_HH-mm-ss>.zip`;
  - a button line like ArcaneChat's: round pills, Cancel alone on the left, Import and Export on the right;
  - an unset directory is shown in red, a set one in yellow.
- **Dialogs:**
  - Success is a black-yellow OK dialog with a **yellow border**.
  - After Export, OK closes the dialog, the panel and the UI page.
  - After Import, "Later" or "Restart now" closes the whole chain.
  - Failures leave the panel open.
- **On every entry** the app checks all-files access and the battery exemption, and asks for them.
