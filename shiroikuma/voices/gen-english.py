#!/usr/bin/env python3
"""Build the 白い熊 音声 English voice catalogue and bundled samples (Kokoro-82M via sherpa-onnx).

Writes (committed; small):
  app/src/onse/assets/voices/english.json         the model download (URL, size, SHA-256) + voices
  app/src/onse/assets/voices/english/<voice>.ogg   one short sample per voice

Uses the same model release the phone downloads: k2-fsa/sherpa-onnx tts-models
kokoro-int8-multi-lang-v1_0 (Apache-2.0), cached in .scratch/en/. Speaker ids are the documented
v1_0 ones (https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html).

Usage: .scratch/venv/bin/python shiroikuma/voices/gen-english.py
"""
import hashlib, json, os, subprocess, tarfile, urllib.request, wave
import numpy as np
import sherpa_onnx

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODEL = "kokoro-int8-multi-lang-v1_0"
URL = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/{MODEL}.tar.bz2"
CACHE = os.path.join(ROOT, ".scratch/en")
OUT = os.path.join(ROOT, "app/src/onse/assets/voices")
DEFAULT = "am_michael"   # 白い熊's pick, 2026-10-01
SAMPLE_TEXT = "Hello. This is a sample of my English voice."
NAMES = ["af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova",
         "af_river", "af_sarah", "af_sky", "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam",
         "am_michael", "am_onyx", "am_puck", "am_santa", "bf_alice", "bf_emma", "bf_isabella", "bf_lily",
         "bm_daniel", "bm_fable", "bm_george", "bm_lewis"]
# Files the phone keeps from the archive (English only — the Chinese parts are dropped).
KEEP = ["model.int8.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt", "lexicon-gb-en.txt", "espeak-ng-data/", "LICENSE"]


def main():
    os.makedirs(CACHE, exist_ok=True)
    tar = os.path.join(CACHE, f"{MODEL}.tar.bz2")
    if not os.path.exists(tar):
        urllib.request.urlretrieve(URL, tar + ".part"); os.rename(tar + ".part", tar)
    k = os.path.join(CACHE, MODEL)
    if not os.path.isdir(k):
        tarfile.open(tar).extractall(CACHE)
    sha = hashlib.sha256(open(tar, "rb").read()).hexdigest()
    os.makedirs(os.path.join(OUT, "english"), exist_ok=True)
    engines = {}
    voices = []
    for sid, name in enumerate(NAMES):
        accent = "US" if name[0] == "a" else "UK"
        lex = "lexicon-us-en.txt" if accent == "US" else "lexicon-gb-en.txt"
        if lex not in engines:
            engines[lex] = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
                kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(model=f"{k}/model.int8.onnx", voices=f"{k}/voices.bin",
                    tokens=f"{k}/tokens.txt", data_dir=f"{k}/espeak-ng-data", lexicon=f"{k}/{lex}"), num_threads=4)))
        a = engines[lex].generate(SAMPLE_TEXT, sid=sid, speed=1.0)
        pcm = (np.clip(np.array(a.samples), -1, 1) * 32767).astype(np.int16)
        wav = os.path.join(CACHE, "sample.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(a.sample_rate); w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", wav, "-ac", "1", "-c:a", "libopus",
                        "-b:a", "48k", "-application", "audio", os.path.join(OUT, "english", f"{name}.ogg")], check=True)
        voices.append({"sid": sid, "name": name, "accent": accent, "female": name[1] == "f",
                       "sample": f"english/{name}.ogg"})
    catalog = {"model": MODEL, "url": URL, "size": os.path.getsize(tar), "sha256": sha, "sampleRate": 24000,
               "keep": KEEP, "defaultVoice": DEFAULT, "sampleText": SAMPLE_TEXT, "voices": voices}
    json.dump(catalog, open(os.path.join(OUT, "english.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(len(voices), "voices; samples",
          sum(os.path.getsize(os.path.join(OUT, "english", f)) for f in os.listdir(os.path.join(OUT, "english"))) // 1024, "KiB")


if __name__ == "__main__":
    main()
