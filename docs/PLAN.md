# shiroikuma-onse — plan

Agreed with 白い熊 on 2026-09-29 as Component 1 of the 言語島 (gengoshima) Japanese Language Islands
suite. The whole suite plan: `~/.claude/plans/cosmic-strolling-bubble.md`.

## Scope

A generic VOICEVOX sister app:

- **Base:** fork of `0266st/VOICEVOX_TTS_Engine_For_Android` (MIT). Fold in upstream PR #19 (branch
  `0266st/core-0.17`): voicevox_core **0.17.0** AAR, VOICEVOX onnxruntime 1.23.2, models fetched by a
  Gradle task instead of being copied on every launch. When upstream merges it, the rebase drops our copy.
- **Voice:** VOICEVOX **No.7 / 読み聞かせ, style id 31** — the voice of `shiroikuma-jisho-subtitles`
  (PC engine 0.25.2, `speedScale` 1.15, `pitchScale` 0, `intonationScale` 1.0, pre/post phoneme 0,
  0.25 s between sentences). Ship the `.vvm` that holds style 31 (find it in
  `~/.cache/jisho-subs/vv-engine/`). Credit “VOICEVOX:No.7” on an About screen, per the voice's terms.
- **Keep the system TTS engine** (`TextToSpeechService`), so doksho and others can use the voice.
- **Render contract** for other apps — written up as `sister-app-contract-onse-render.md` in
  `~/git/shiroikuma-jiyusagyoban`, following the existing contracts (automation-v2 token, string
  extras only, reply by a fresh broadcast, no Binder / PendingIntent / ResultReceiver — EMUI drops them):
  - request `shiroikuma.onse.action.RENDER`: `request_id`, `reply_package`, `batch_path` (a JSON file
    of `{id, text, out_path}`), `speaker`, `speed`, `pitch`, `intonation`, `gap_pre`, `gap_post`;
  - replies `shiroikuma.onse.action.RENDERED` per item: `{request_id, id, status, out_path,
    duration_ms, error}`, then a final `DONE`;
  - a `RenderService` foreground service loads the model once and synthesises sequentially;
  - encode **OGG/Opus** in-app: MediaCodec Opus encoder + `MediaMuxer` `MUXER_OUTPUT_OGG` (API 29+),
    mono 24 kHz, ~32 kbps; write `<name>.ogg.part` then rename;
  - all-files access, to write under `/sdcard/〇/[227] 日本語/[227][727] 言語島/`.
- **Reading control:** callers send text with overridden words already replaced by katakana; later
  option: expose `createAudioQueryFromKana` for accent-level control.
- **Sister-app backup hand-off:** the 保存復元 automation contract of `shiroikuma-jiyusagyoban`
  (`sister-app-contract-backup-automation-hand-off.md`), as in the other sister apps.

## Step order (白い熊, 2026-09-29)

1. ✅ Repo, remotes, branches, fork layer, keystore, skills — committed and pushed.
2. ✅ Our black-yellow traced icon: PNG previews of ours and the original in `~/tmp` → 白い熊 confirms.
3. ✅ De-branding: remove the upstream name, GitHub links and branding everywhere (all pages, Help …),
   put in 白い熊 音声, our GitHub link and our icon wherever an icon shows.
4. ✅ First build, then push.
5. ✅ Bring the UI in line with the sister repos (study `shiroikuma-denwa`, `shiroikuma-messeji`, …) and
   build the **白い熊 音声 UI** page (below).
6. The render contract and OGG encoding (✅ core 0.17, No.7 bundled, every voice downloadable with bundled samples).
7. ✅ The backup-automation hand-off from `shiroikuma-jiyusagyoban` (contract v2, `app/src/onse/java/shiroikuma/onse/automation/`). Still open on jiyusagyoban's side: `shiroikuma.onse` in its `<queries>` and a 「保存 ⇨ shiroikuma.onse」 roster task.

## The 白い熊 音声 UI page — 白い熊's requirements (2026-09-29)

- Name **白い熊 音声 UI**; a **long-press on the Settings cog** of the main screen opens it directly.
- Holds as configurable items **all our changes and modifiable configs** (details to follow from 白い熊).
- Built like the sister repos: logical sectioning; **each category a bold, big, underlined heading**
  (underline only as wide as the text), items **significantly indented**, each sub-level further
  indented; thin spacers between sections; **tight lines, no big padding** — padding only between
  top-level groups. Same look for headings, sizes and separators as the **kxkb UI page**.
- Apparent UI options: colours, fonts, etc. **Black-yellow default** for almost everything (black
  background, yellow text, yellow border), all of it settable, grouped logically.
- **Colour selectors:** four RGBA sliders with a preview, and above them one-click boxes pre-filled
  with previously selected colours.
- **External fonts** like the sister repos; font choices render **in their own glyphs**.
- **All size selectors are sliders** (font size, weight, roundness …); borders and the like go down
  to 0. **Everything previews live** (text, icon, border thickness, size, colour).
- **Export/Import is the first section at the top**, as in the Kōjiki UI page (same idea and flow):
  a settable directory, queried on opening the page for the latest export; every settable item
  organised and split logically. Backup file names follow the family rule `shiroikuma-onse_<yyyy-MM-dd_HH-mm-ss>.zip`.
- **Button line like ArcaneChat's:** round pills, Cancel alone on the left, Import and Export on the right.
- **Directory unset → red** message (on the page too); set → yellow.
- **Success:** a black-yellow OK dialog with a **yellow border**. After Export, OK closes the dialog,
  the Export/Import panel beneath it and the UI page. After Import, acknowledging (“Later”, or
  “Restart now”, which restarts the app) closes the whole chain the same way.
- **Failures** (“Export failed…”, “No categories selected.”) leave the panel open.
