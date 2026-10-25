<div align="center">

<img src="shiroikuma/icon/onse-icon-512.png" width="120" alt="白い熊 音声 icon" />

# 白い熊 音声

**Offline speech on Android in Japanese, English and Czech — a system text-to-speech voice and an intent-driven audio render service for the 白い熊 sister apps.**

A fork of [VOICEVOX TTS Engine for Android](https://github.com/0266st/VOICEVOX_TTS_Engine_For_Android) with **major additions**: every VOICEVOX voice, English (Kokoro) and Czech (Piper) voices, a render service that writes OGG/Opus files for other apps, a full main screen and a themable settings page with backup and restore.

Installs **side-by-side** with the original (app id `shiroikuma.onse`).

**📥 Latest release: [`1.0+014`](https://github.com/ShiroiKuma0/shiroikuma-onse/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-onse/releases)

</div>

---

## 🎙️ Every VOICEVOX voice
All 27 models of VOICEVOX 0.17 — 218 styles, singing voices included — listed with a bundled sample
for each, so you hear a voice before you download it. **No.7（読み聞かせ）** ships in the APK and is the
default at speed 1.15; every other model downloads in-app, verified by SHA-256.

---

## 🌐 English and Czech too
**English:** Kokoro-82M with 28 US and UK voices (default am_michael). **Czech:** Piper’s **jirka**
(male) and **kasandra** (female). Both run through sherpa-onnx, fully offline; each model downloads
in-app on request, and the try-it box picks the language from the text you type.

---

## 📼 A render service for sister apps
Other apps send a batch of sentences by intent — language, voice, speed, pitch, bitrate per
request — and get one OGG/Opus file per sentence at the exact path they name, written atomically,
with a reply per item — for Japanese including the kana reading VOICEVOX actually spoke, so a
caller can catch a misread word and resend it in katakana. 言語島, the Japanese Language Islands suite in 白い熊 自由作業盤, makes all
its Japanese, English and Czech audio here. The contract: [`docs/sister-app-contract-onse-render.md`](docs/sister-app-contract-onse-render.md).

---

## 🔊 A system TTS engine
Choose **白い熊 音声** in Android’s text-to-speech settings and every app that reads aloud speaks with
it. Every installed style is its own Android voice; speed, pitch, intonation, volume and pauses come
from the app.

---

## 🧭 One screen per language
The main screen shows the engine status and a big try-it box, then a section each for Japanese,
English and Czech: the current voice and all its settings on top, every other voice folded away.

---

## 🎨 白い熊 音声 UI and backups
A settings page with live-previewed colours, fonts, borders and shapes, black-yellow by default.
Export / Import writes the whole state to a dated ZIP; 白い熊 応用管理 and 自由作業盤 can back it up
and restore it by automation, and a restore re-downloads the voices it lists.

---

## Built on VOICEVOX TTS Engine for Android
A fork of [VOICEVOX TTS Engine for Android](https://github.com/0266st/VOICEVOX_TTS_Engine_For_Android)
(app id `shiroikuma.onse`, so it coexists with the original), which brought VOICEVOX CORE to
Android’s speech system. The code remains under the MIT License (see [`LICENSE`](LICENSE)).
What changed, release by release: [`CHANGELOG.md`](CHANGELOG.md).

## Credits and licences

- [VOICEVOX CORE](https://github.com/VOICEVOX/voicevox_core) and the [VOICEVOX voice models](https://github.com/VOICEVOX/voicevox_vvm) — MIT License for the code. **Each voice has its own terms of use** (see each character’s page on [voicevox.hiroshiba.jp](https://voicevox.hiroshiba.jp/)); audio made with it carries its credit, e.g. **VOICEVOX:No.7**.
- [VOICEVOX ONNX Runtime](https://github.com/VOICEVOX/onnxruntime-builder) (from [ONNX Runtime](https://github.com/microsoft/onnxruntime)) — MIT License.
- [OpenJTalk](https://open-jtalk.sourceforge.net/) — Copyright (c) 2009, Nara Institute of Science and Technology, Japan.
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Apache License 2.0; it phonemises with [espeak-ng](https://github.com/espeak-ng/espeak-ng) (GPL-3.0).
- [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) — Apache License 2.0.
- [Piper](https://github.com/rhasspy/piper) cs_CZ voices: **jirka** (dataset CC0) and **kasandra** (dataset CC BY 4.0, Ondřej Šimek; converted for sherpa-onnx and hosted on this repository’s [`voices`](https://github.com/ShiroiKuma0/shiroikuma-onse/releases/tag/voices) release).
- [Gson](https://github.com/google/gson) and [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) — Apache License 2.0.

## Building

```bash
git clone https://github.com/ShiroiKuma0/shiroikuma-onse.git && cd shiroikuma-onse
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=$HOME/android-sdk
bash ./gradlew :app:downloadVoicevox     # once: VOICEVOX CORE into the local Maven repository
bash ./gradlew buildFork                 # signed arm64-v8a APK (needs keystore.properties)
```

Install the APK on an arm64 Android phone, open **白い熊 音声**, and grant the access it asks for.
