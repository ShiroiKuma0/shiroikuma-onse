# Sister-app contract — 白い熊 音声 render service

**Callee:** `shiroikuma.onse` (白い熊 音声). **First caller:** 白い熊 自由作業盤's 言語島 (日本語 [227]).
Revision 2026-09-30.

A caller hands 音声 a batch of Japanese sentences and gets back one audio file per sentence, written
**exactly at the absolute path the caller names**. 音声 knows nothing about where or how the caller
lays its files out: every directory and file name is the caller's (for 言語島, all of it is set in
`日本語の設定 -- [227][01]`). Same family rules as the 保存復元 contract: string extras only, replies
as fresh broadcasts with `setPackage` + `FLAG_INCLUDE_STOPPED_PACKAGES`, no Binder, no
PendingIntent, no ordered result.

## Request — broadcast `shiroikuma.onse.action.RENDER`

Explicit to package `shiroikuma.onse`, with `--include-stopped-packages` semantics
(`FLAG_INCLUDE_STOPPED_PACKAGES`).

| extra | required | meaning |
| --- | --- | --- |
| `request_id` | yes | correlation id, echoed on every reply |
| `reply_action` | yes | action of the reply broadcasts |
| `reply_package` | yes | package the replies are sent to |
| `batch_path` | yes | absolute path of a JSON file `{"items":[{"id":…,"text":…,"out_path":…}, …]}` |
| `speaker` | no | VOICEVOX style id (default `31`, No.7 / 読み聞かせ). Its model must be installed in 音声 |
| `speed` | no | speedScale as a decimal (default `1.15`) |
| `pitch` | no | pitchScale, −0.15…+0.15 (default `0`) |
| `intonation` | no | intonationScale (default `1.0`) |
| `volume` | no | volumeScale (default `1.0`) |
| `gap_pre`, `gap_post` | no | silence before / after, in seconds (default `0.1`) |
| `bitrate_kbps` | no | Opus bitrate (default `32`) |
| `format` | no | `ogg` (default: OGG/Opus, mono 24 kHz) or `wav` |
| `token` | no | only checked when 「Use authorization token?」 is on in 白い熊 音声 UI |

- `text` is what VOICEVOX reads. A caller controlling pronunciation replaces a word by its
  **katakana** reading before sending; VOICEVOX reads katakana verbatim.
- `out_path` must be absolute. Parent directories are created. The file is written as
  `<out_path>.part` and renamed over any existing file only when complete, so a re-render replaces
  the old file atomically and a reader never sees a half file.
- 音声 needs All-Files access to write outside its own storage.

## Replies — broadcasts of `reply_action` to `reply_package`

Per item, as soon as its file is in place (or failed):

| extra | value |
| --- | --- |
| `request_id` | as sent |
| `event` | `item` |
| `id` | the item's `id` |
| `status` | `OK` or `ERROR` |
| `out_path` | the item's `out_path` |
| `duration_ms` | audio length, integer string |
| `index`, `total` | 1-based position of the item and the batch size |
| `error` | on `ERROR` only, one line |

Then exactly one terminal reply:

| extra | value |
| --- | --- |
| `request_id` | as sent |
| `event` | `done` |
| `result` | `OK:<ok>|<failed>|<total>`, or `ERROR:<reason>` (a reason may be followed by `|<ok>|<failed>|<total>`) |

Reasons worth keying on: `automation disabled`, `bad token`, `no-storage-access`,
`no-foreground-start`, `voice not installed: <style> (<model file>)`, `unknown speaker: <style>`,
`no-opus-encoder`, `cancelled`.

## Cancel — broadcast `shiroikuma.onse.action.CANCEL_RENDER`

Extras `request_id` (and `token` if required). Stops that request at its next item boundary; the
items already answered stay on disk. The request then answers `ERROR:cancelled|<ok>|<failed>|<total>`.
The cancel itself answers nothing; for an unknown id it is a silent no-op.

## Behaviour

- Requests queue and run one at a time on a foreground service; synthesis takes a few seconds per
  sentence on the phone.
- Gate: the same switch and optional token as 音声's 保存復元 automation (shipped ON, token off).
- The caller's manifest must list `shiroikuma.onse` in `<queries>`, and 音声 lists
  `shiroikuma.jiyusagyoban`, so the replies' `setPackage` resolves on Android 11+.
