import onnxruntime as ort
import numpy as np
from PIL import Image
from pathlib import Path

ROOT = Path(r"C:\Users\Anup Kumar\AndroidStudioProjects\RSQ\kaggle_dataset\test")
MODEL = r"C:\Users\Anup Kumar\AndroidStudioProjects\RSQ\ml\models\rsq_mobilenetv3_best.onnx"

CLASSES = [
    "COLLAPSED_STRUCTURE",
    "FIRE_SMOKE",
    "FLOOD",
    "NORMAL",
]

session = ort.InferenceSession(
    MODEL,
    providers=["CPUExecutionProvider"]
)

input_name = session.get_inputs()[0].name
output_name = session.get_outputs()[0].name

print("ONNX INPUT:", input_name)
print("ONNX OUTPUT:", output_name)
print()

for actual_class in CLASSES:
    image_path = sorted((ROOT / actual_class).glob("*"))[0]

    image = Image.open(image_path).convert("RGB")
    image = image.resize((224, 224))

    x = np.asarray(image, dtype=np.float32) / 255.0

    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32)

    x = (x - mean) / std
    x = np.transpose(x, (2, 0, 1))
    x = np.expand_dims(x, axis=0).astype(np.float32)

    logits = session.run(
        [output_name],
        {input_name: x}
    )[0][0]

    exp_logits = np.exp(logits - np.max(logits))
    probabilities = exp_logits / exp_logits.sum()

    predicted_index = int(np.argmax(probabilities))

    print("=" * 60)
    print("ACTUAL:     ", actual_class)
    print("IMAGE:      ", image_path.name)
    print("PREDICTED:  ", CLASSES[predicted_index])
    print("CONFIDENCE: ", f"{probabilities[predicted_index]:.6f}")
    print("PROBABILITIES:")

    for i, class_name in enumerate(CLASSES):
        print(f"  {class_name}: {probabilities[i]:.6f}")

print("=" * 60)
