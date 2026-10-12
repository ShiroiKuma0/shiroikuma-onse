#!/usr/bin/env python3
"""Build the 白い熊 音声 Czech voice catalogue and bundled samples (Piper cs_CZ via sherpa-onnx).

Writes (committed; small):
  app/src/onse/assets/voices/czech.json          every voice: its model download (URL, size, SHA-256)
  app/src/onse/assets/voices/czech/<voice>.ogg    one short sample per voice

Each Piper voice is its own model, downloaded on the phone on its own:
  jirka     (male, dataset CC0)      — k2-fsa/sherpa-onnx tts-models vits-piper-cs_CZ-jirka-medium
  kasandra  (female, dataset CC BY 4.0, Ondřej Šimek) — not packaged by k2-fsa: this script takes
            the raw voice from rhasspy/piper-voices, makes it loadable by sherpa-onnx
            (convert-piper.py), packs it as vits-piper-cs_CZ-kasandra-medium.tar.bz2 in .scratch/cs/
            and points the catalogue at our GitHub release `voices`, where that archive is uploaded
            (`gh release upload voices .scratch/cs/vits-piper-cs_CZ-kasandra-medium.tar.bz2`).

Piper renders at 22.05 kHz; the phone resamples to 24 kHz for Opus.

Usage: .scratch/venv/bin/python shiroikuma/voices/gen-czech.py
"""
import hashlib, json, os, shutil, subprocess, tarfile, urllib.request, wave
import numpy as np
import sherpa_onnx

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CACHE = os.path.join(ROOT, ".scratch/cs")
OUT = os.path.join(ROOT, "app/src/onse/assets/voices")
SHERPA = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
OURS = "https://github.com/ShiroiKuma0/shiroikuma-onse/releases/download/voices"
PIPER_HF = "https://huggingface.co/rhasspy/piper-voices/resolve/main"
DEFAULT = "jirka"
SAMPLE_TEXT = "Dobrý den. Toto je ukázka mého českého hlasu."
VOICES = [
    {"name": "jirka", "female": False, "license": "dataset CC0",
     "model": "vits-piper-cs_CZ-jirka-medium", "onnx": "cs_CZ-jirka-medium.onnx", "host": SHERPA},
    {"name": "kasandra", "female": True, "license": "dataset CC BY 4.0, Ondřej Šimek",
     "model": "vits-piper-cs_CZ-kasandra-medium", "onnx": "cs_CZ-kasandra-medium.onnx", "host": OURS,
     "piper": "cs/cs_CZ/kasandra/medium"},
]


def fetch(url, path):
    if not os.path.exists(path):
        urllib.request.urlretrieve(url, path + ".part"); os.rename(path + ".part", path)


def pack_from_piper(v):
    """Raw Piper voice → a k2-fsa-style vits-piper-* archive, built in CACHE."""
    tar = os.path.join(CACHE, f"{v['model']}.tar.bz2")
    if os.path.exists(tar):
        return tar
    d = os.path.join(CACHE, v["model"])
    os.makedirs(d, exist_ok=True)
    for f in (v["onnx"], v["onnx"] + ".json", "MODEL_CARD"):
        fetch(f"{PIPER_HF}/{v['piper']}/{f}", os.path.join(d, f))
    subprocess.run([os.path.join(ROOT, ".scratch/venv/bin/python"), os.path.join(ROOT, "shiroikuma/voices/convert-piper.py"),
                    d, v["onnx"][:-5]], check=True)
    # The espeak-ng data is the same as in k2-fsa's own Czech package.
    if not os.path.isdir(os.path.join(d, "espeak-ng-data")):
        shutil.copytree(os.path.join(CACHE, VOICES[0]["model"], "espeak-ng-data"), os.path.join(d, "espeak-ng-data"))
    with open(os.path.join(d, "MODEL_CARD"), "a", encoding="utf-8") as f:
        f.write(f"\n## Packaging\n\nFrom rhasspy/piper-voices {v['piper']}, with sherpa-onnx metadata and tokens.txt added"
                " by shiroikuma-onse's shiroikuma/voices/convert-piper.py, for 白い熊 音声.\n")
    with tarfile.open(tar + ".part", "w:bz2") as t:
        for f in (v["onnx"], "tokens.txt", "MODEL_CARD", "espeak-ng-data"):
            t.add(os.path.join(d, f), arcname=f"{v['model']}/{f}")
    os.rename(tar + ".part", tar)
    return tar


def main():
    os.makedirs(CACHE, exist_ok=True)
    os.makedirs(os.path.join(OUT, "czech"), exist_ok=True)
    voices = []
    for sid, v in enumerate(VOICES):
        if "piper" in v:
            tar = pack_from_piper(v)
        else:
            tar = os.path.join(CACHE, f"{v['model']}.tar.bz2")
            fetch(f"{v['host']}/{v['model']}.tar.bz2", tar)
            if not os.path.isdir(os.path.join(CACHE, v["model"])):
                tarfile.open(tar).extractall(CACHE)
        d = os.path.join(CACHE, v["model"])
        tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=f"{d}/{v['onnx']}", tokens=f"{d}/tokens.txt",
                data_dir=f"{d}/espeak-ng-data"), num_threads=4)))
        a = tts.generate(SAMPLE_TEXT, sid=0, speed=1.0)
        pcm = (np.clip(np.array(a.samples), -1, 1) * 32767).astype(np.int16)
        wav = os.path.join(CACHE, "sample.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(a.sample_rate); w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", wav, "-ac", "1", "-ar", "24000",
                        "-c:a", "libopus", "-b:a", "48k", "-application", "audio",
                        os.path.join(OUT, "czech", f"{v['name']}.ogg")], check=True)
        voices.append({"name": v["name"], "female": v["female"], "license": v["license"],
                       "sample": f"czech/{v['name']}.ogg", "model": v["model"], "onnx": v["onnx"],
                       "url": f"{v['host']}/{v['model']}.tar.bz2", "size": os.path.getsize(tar),
                       "sha256": hashlib.sha256(open(tar, "rb").read()).hexdigest(), "sampleRate": tts.sample_rate})
    catalog = {"defaultVoice": DEFAULT, "sampleText": SAMPLE_TEXT,
               "keep": ["tokens.txt", "espeak-ng-data/", "MODEL_CARD"], "voices": voices}
    json.dump(catalog, open(os.path.join(OUT, "czech.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(len(voices), "voices:", ", ".join(f"{v['name']} {v['size']} B" for v in voices))


if __name__ == "__main__":
    main()
