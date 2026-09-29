<div align="center">

<img src="shiroikuma/icon/onse-icon-512.png" width="120" alt="白い熊 音声 icon" />

# 白い熊 音声

**VOICEVOX on Android — a system text-to-speech voice and an intent-driven audio render service for the 白い熊 sister apps.**

A fork of [VOICEVOX TTS Engine for Android](https://github.com/0266st/VOICEVOX_TTS_Engine_For_Android).

Installs **side-by-side** with the original (app id `shiroikuma.onse`).

**📥 [All releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-onse/releases)**

</div>

## What it is

- **A system TTS engine.** Pick **白い熊 音声** in Android's text-to-speech settings and every app that
  reads aloud speaks with VOICEVOX — fully offline, on the phone.
- **A render service for sister apps** *(in development)*: other 白い熊 apps hand it a batch of
  Japanese sentences by intent and get back OGG/Opus files — first of all the 言語島 Japanese Language
  Islands suite in 白い熊 自由作業盤.
- **The 白い熊 voice** *(in development)*: VOICEVOX No.7 / 読み聞かせ, the same voice the PC side uses.

See [`docs/PLAN.md`](docs/PLAN.md) for the roadmap and [`CHANGELOG.md`](CHANGELOG.md) for what changed.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=$HOME/android-sdk
bash ./gradlew :app:downloadVoicevox     # once: VOICEVOX CORE into the local Maven repository
bash ./gradlew buildFork                 # signed arm64-v8a APK (needs keystore.properties)
```

## Credits and licences

- Built on [VOICEVOX TTS Engine for Android](https://github.com/0266st/VOICEVOX_TTS_Engine_For_Android) — MIT License (see [`LICENSE`](LICENSE)).
- Voice: **VOICEVOX:冥鳴ひまり**.
- [VOICEVOX CORE](https://github.com/VOICEVOX/voicevox_core) — MIT License.
- [OpenJTalk](https://open-jtalk.sourceforge.net/) — Copyright (c) 2009, Nara Institute of Science and Technology, Japan.
- [VOICEVOX ONNX Runtime](https://github.com/VOICEVOX/onnxruntime-builder) (from [ONNX Runtime](https://github.com/microsoft/onnxruntime)) — MIT License.
- [Gson](https://github.com/google/gson) — Apache License 2.0.
