#!/usr/bin/env python3
"""Build the 白い熊 音声 voice catalogue and the bundled voice samples.

For every model of the official voicevox_vvm release the app downloads from, this records the
file, its size and SHA-256, and every character / style inside it — then synthesises one short
Japanese sample per style with the SAME release model and VOICEVOX CORE version the phone uses,
and encodes it to a small OGG/Opus file. The app bundles the catalogue and the samples, so every
voice can be auditioned offline before its model is downloaded.

Outputs (committed; small):
  app/src/onse/assets/voices/catalog.json
  app/src/onse/assets/voices/samples/<style id>.ogg

Inputs (gitignored, re-fetched by this script if missing):
  .scratch/vvm-<release>/<n>.vvm            the voicevox_vvm release models
  .scratch/venv                             python venv with the voicevox_core wheel
  .scratch/dl/voicevox_onnxruntime-linux-x64-<ver>/lib/libvoicevox_onnxruntime.so
  app/src/main/res/raw/open_jtalk_dict.zip  upstream's OpenJTalk dictionary

Usage: .scratch/venv/bin/python shiroikuma/voices/gen-voices.py
"""
import hashlib
import io
import json
import os
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

from voicevox_core import Note, Score
from voicevox_core.blocking import Onnxruntime, OpenJtalk, Synthesizer, VoiceModelFile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RELEASE = "0.17.0"          # voicevox_vvm release — keep equal to libs.versions.toml voicevoxVvm
ORT = "1.23.2"
BASE_URL = f"https://github.com/VOICEVOX/voicevox_vvm/releases/download/{RELEASE}"
MODELS = [str(n) for n in range(25)] + ["n0", "s0"]
BUNDLED = "6"               # No.7 — shipped inside the APK as res/raw/model.vvm
DEFAULT_STYLE = 31          # No.7 / 読み聞かせ — the 白い熊 voice (shiroikuma-jisho-subtitles)
SAMPLE_TEXT = "こんにちは。これは声の見本です。"

VVM_DIR = os.path.join(ROOT, f".scratch/vvm-{RELEASE}")
ORT_LIB = os.path.join(ROOT, f".scratch/dl/voicevox_onnxruntime-linux-x64-{ORT}/lib/libvoicevox_onnxruntime.so")
DICT_ZIP = os.path.join(ROOT, "app/src/main/res/raw/open_jtalk_dict.zip")
OUT_DIR = os.path.join(ROOT, "app/src/onse/assets/voices")
SAMPLES = os.path.join(OUT_DIR, "samples")

# A short sung phrase (ド・レ・ミ・レ・ド on ら) for the singing styles: frame lengths at 93.75 fps.
SONG = [(None, "", 15), (60, "ら", 22), (62, "ら", 22), (64, "ら", 22), (62, "ら", 22), (60, "ら", 40), (None, "", 15)]


def fetch(name):
    path = os.path.join(VVM_DIR, name)
    if not os.path.exists(path):
        os.makedirs(VVM_DIR, exist_ok=True)
        print("downloading", name)
        urllib.request.urlretrieve(f"{BASE_URL}/{name}", path + ".part")
        os.rename(path + ".part", path)
    return path


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def to_ogg(wav: bytes, out: str):
    subprocess.run(
        ["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-f", "wav", "-i", "pipe:0",
         "-ac", "1", "-c:a", "libopus", "-b:a", "20k", "-application", "voip", out],
        input=wav, check=True)


def main():
    os.makedirs(SAMPLES, exist_ok=True)
    ort = Onnxruntime.load_once(filename=ORT_LIB)
    with tempfile.TemporaryDirectory() as tmp:
        zipfile.ZipFile(DICT_ZIP).extractall(tmp)
        dict_dir = next(os.path.join(tmp, d) for d in os.listdir(tmp)
                        if os.path.isdir(os.path.join(tmp, d)))
        synth = Synthesizer(ort, OpenJtalk(dict_dir))
        models = []
        for m in MODELS:
            path = fetch(f"{m}.vvm")
            with VoiceModelFile.open(path) as vm:
                synth.load_voice_model(vm)
                metas = vm.metas
                model_id = vm.id
            teacher = None
            characters = []
            for ch in metas:
                styles = []
                for st in ch.styles:
                    kind = str(st.type)
                    # 0.17 speech styles are "streaming_talk" (older: "talk"); in the singing model
                    # the "sing" style is the teacher that builds the sung query and the
                    # "frame_decode" styles are the voices that render it.
                    if kind in ("sing", "singing_teacher"):
                        teacher = st.id
                    styles.append({"id": st.id, "name": st.name, "type": kind})
                characters.append({"name": ch.name, "uuid": ch.speaker_uuid, "styles": styles})
            singing = False
            for ch in characters:
                for st in ch["styles"]:
                    sample = os.path.join(SAMPLES, f"{st['id']}.ogg")
                    st["sample"] = None
                    wav = None
                    if st["type"] in ("talk", "streaming_talk"):
                        wav = synth.tts(SAMPLE_TEXT, st["id"])
                    elif st["type"] == "frame_decode" and teacher is not None:
                        singing = True
                        score = Score([Note(n, l, key=k) if k is not None else Note(n, l)
                                       for k, l, n in SONG])
                        q = synth.create_sing_frame_audio_query(score, teacher)
                        wav = synth.frame_synthesis(q, st["id"])
                    elif st["type"] in ("sing", "singing_teacher"):
                        singing = True
                    if wav:
                        to_ogg(wav, sample)
                        st["sample"] = f"samples/{st['id']}.ogg"
            synth.unload_voice_model(model_id)
            models.append({
                "file": f"{m}.vvm",
                "url": f"{BASE_URL}/{m}.vvm",
                "size": os.path.getsize(path),
                "sha256": sha256(path),
                "bundled": m == BUNDLED,
                "singing": singing,
                "characters": characters,
            })
            print(m, ", ".join(c["name"] for c in characters))
    catalog = {"release": RELEASE, "defaultStyle": DEFAULT_STYLE, "sampleText": SAMPLE_TEXT, "models": models}
    with open(os.path.join(OUT_DIR, "catalog.json"), "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    total = sum(os.path.getsize(os.path.join(SAMPLES, n)) for n in os.listdir(SAMPLES))
    print(f"catalog: {len(models)} models; samples {len(os.listdir(SAMPLES))} files, {total/1024:.0f} KiB")


if __name__ == "__main__":
    sys.exit(main())
