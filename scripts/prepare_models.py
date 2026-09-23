#!/usr/bin/env python3
"""Opt-in local model provisioning. Verify upstream bytes; never silently accept a new revision."""
import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import shutil
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    "yolo11n.pt": (
        "https://huggingface.co/Ultralytics/YOLO11/resolve/8b8ac7d1fae7468f85dbf89670dd66f41f485aab/yolo11n.pt",
        "0ebbc80d4a7680d14987a577cd21342b65ecfd94632bd9a8da63ae6417644ee1",
    ),
    "plate.onnx": (
        "https://huggingface.co/morsetechlab/yolov11-license-plate-detection/resolve/251a30d7daedca065f56e04b0af04052c907c68f/license-plate-finetune-v1n.onnx",
        "693133a1db97a3ba1e90068986f80afb72c3fcddb681e57181a89a9a3dc351d6",
    ),
    "bus.jpg": (
        "https://raw.githubusercontent.com/ultralytics/ultralytics/v8.3.221/ultralytics/assets/bus.jpg",
        "c02019c4979c191eb739ddd944445ef408dad5679acab6fd520ef9d434bfbc63",
    ),
}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def download(name, folder):
    url, expected = SOURCES[name]
    target = folder / name
    if not target.exists() or digest(target) != expected:
        temporary = target.with_suffix(target.suffix + ".download")
        urllib.request.urlretrieve(url, temporary)
        if digest(temporary) != expected:
            temporary.unlink()
            raise RuntimeError(f"Upstream checksum mismatch: {name}")
        temporary.replace(target)
    return target

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-agpl", action="store_true", help="Prepare AGPL model assets for local use; review models/README.md before distribution")
    args = parser.parse_args()
    if not args.accept_agpl:
        parser.error("Explicit --accept-agpl required. No download or license change has been performed.")
    cache = ROOT / ".tools/models"
    cache.mkdir(parents=True, exist_ok=True)
    assets = ROOT / "app/src/main/assets/models"
    assets.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("YOLO_CONFIG_DIR", str(ROOT / ".tools/yolo-config"))
    os.environ.setdefault("MPLCONFIGDIR", str(ROOT / ".tools/matplotlib"))
    from ultralytics import YOLO
    import onnx
    import onnxruntime as ort
    import numpy as np
    import torch
    weights = download("yolo11n.pt", cache)
    plate = download("plate.onnx", cache)
    exported = Path(YOLO(str(weights)).export(format="onnx", imgsz=640, dynamic=False, simplify=False, opset=17, nms=False, device="cpu"))
    manifest = {"schema": 1, "runtime": "onnxruntime-android:1.23.2", "exportTorch": torch.__version__, "models": {}}
    for source, name, classes in [(exported, "vehicle.onnx", 80), (plate, "plate.onnx", 1)]:
        model = onnx.load(source)
        # Fix the dynamic plate interface to the single shape actually supported by this app.
        for tensor, shape in [(model.graph.input[0], [1, 3, 640, 640]), (model.graph.output[0], [1, classes + 4, 8400])]:
            for dimension, value in zip(tensor.type.tensor_type.shape.dim, shape):
                dimension.ClearField("dim_param")
                dimension.dim_value = value
        props = {p.key: p.value for p in model.metadata_props if p.key != "date"}
        arguments = ast.literal_eval(props.get("args", "{}"))
        arguments["dynamic"] = False
        props["args"] = repr(arguments)
        onnx.helper.set_model_props(model, props)
        onnx.checker.check_model(model)
        output = assets / name
        onnx.save(model, output)
        session = ort.InferenceSession(str(output), providers=["CPUExecutionProvider"])
        prediction = session.run(None, {session.get_inputs()[0].name: np.zeros((1, 3, 640, 640), dtype=np.float32)})[0]
        if prediction.shape != (1, classes + 4, 8400) or not np.isfinite(prediction).all():
            raise RuntimeError(f"Invalid model output: {name}")
        original = "yolo11n.pt" if classes == 80 else "plate.onnx"
        manifest["models"][name] = {"sha256": digest(output), "license": "AGPL-3.0", "source": SOURCES[original][0], "sourceSha256": SOURCES[original][1], "classes": classes}
    (assets / "runtime.json").write_text(json.dumps(manifest, indent=2) + "\n")
    image = download("bus.jpg", cache)
    fixtures = ROOT / "app/src/androidTest/assets/detection"
    fixtures.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(image, fixtures / "bus.jpg")
    print(json.dumps(manifest, indent=2))
    print("Prepared local assets. No repository license has been changed; no model has been published.")

if __name__ == "__main__":
    main()
