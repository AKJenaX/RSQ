import torch
import torch.nn as nn
import torchvision.models as models
import onnx
from pathlib import Path

CHECKPOINT = Path(r"C:\Users\Anup Kumar\Downloads\rsq-mobilenetv3-model-v1\rsq_mobilenetv3_best.pth")
OUTPUT = Path(r"C:\Users\Anup Kumar\AndroidStudioProjects\RSQ\ml\models\rsq_mobilenetv3_best.onnx")

# Reconstruct the exact model architecture
checkpoint = torch.load(CHECKPOINT, map_location="cpu")

model = models.mobilenet_v3_large(weights=None)
model.classifier[3] = nn.Linear(
    model.classifier[3].in_features,
    4
)

# Load the actual trained weights
model.load_state_dict(checkpoint["model_state_dict"], strict=True)
model.eval()

# NCHW: [batch, channels, height, width]
dummy_input = torch.randn(1, 3, 224, 224)

OUTPUT.parent.mkdir(parents=True, exist_ok=True)

print("Exporting...")
print("Model:", checkpoint["model_name"])
print("Classes:", checkpoint["classes"])
print("Input:", tuple(dummy_input.shape))

torch.onnx.export(
    model,
    dummy_input,
    str(OUTPUT),
    export_params=True,
    opset_version=15,
    do_constant_folding=True,
    input_names=["input"],
    output_names=["output"],
    dynamic_axes={
        "input": {0: "batch_size"},
        "output": {0: "batch_size"},
    },
)

print(f"ONNX exported: {OUTPUT}")
print(f"ONNX size: {OUTPUT.stat().st_size:,} bytes")

# Validate the ONNX file
onnx_model = onnx.load(str(OUTPUT))
onnx.checker.check_model(onnx_model)

print("ONNX CHECK: PASSED")
print("ONNX model is valid.")
