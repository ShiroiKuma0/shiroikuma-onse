#!/usr/bin/env python3
"""Make a raw Piper voice (rhasspy/piper-voices: <name>.onnx + <name>.onnx.json) loadable by sherpa-onnx.

sherpa-onnx reads a Piper model only when the ONNX file carries its metadata (model_type, comment,
language, voice, has_espeak, n_speakers, sample_rate) and a tokens.txt sits beside it — what
k2-fsa's own vits-piper-* packages contain. This adds both, in place, to <dir>.

Usage: .scratch/venv/bin/python shiroikuma/voices/convert-piper.py <dir> <name>
  e.g. .scratch/venv/bin/python shiroikuma/voices/convert-piper.py .scratch/cs/kasandra cs_CZ-kasandra-medium
"""
import json, os, sys
import onnx


def main(d, name):
    cfg = json.load(open(os.path.join(d, f"{name}.onnx.json"), encoding="utf-8"))
    with open(os.path.join(d, "tokens.txt"), "w", encoding="utf-8") as f:
        for sym, ids in cfg["phoneme_id_map"].items():
            f.write(f"{sym} {ids[0]}\n")
    path = os.path.join(d, f"{name}.onnx")
    m = onnx.load(path)
    meta = {
        "model_type": "vits",
        "comment": "piper",
        "language": cfg["language"]["name_english"],
        "voice": cfg["espeak"]["voice"],
        "has_espeak": "1",
        "n_speakers": str(cfg["num_speakers"]),
        "sample_rate": str(cfg["audio"]["sample_rate"]),
    }
    del m.metadata_props[:]
    for k, v in meta.items():
        m.metadata_props.add(key=k, value=v)
    onnx.save(m, path)
    print(name, meta)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
