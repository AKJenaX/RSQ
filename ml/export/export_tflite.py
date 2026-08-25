import tensorflow as tf
from pathlib import Path
import numpy as np

MODEL_PATH = Path("models/baseline_float32.keras")
TFLITE_PATH = Path("models/disaster_classifier.tflite")
QUANT_TFLITE_PATH = Path("models/disaster_classifier_int8.tflite")
TRAIN_DATA_DIR = Path("data/splits/train")

def representative_data_gen():
    """
    Generator for representative data used during INT8 quantization.
    """
    ds = tf.keras.utils.image_dataset_from_directory(
        TRAIN_DATA_DIR,
        image_size=(224, 224),
        batch_size=1,
        shuffle=True
    ).take(100) # Use 100 images for quantization calibration

    for x, y in ds:
        # Preprocessing must match training: rescaling is part of the model now
        yield [x.numpy().astype(np.float32)]

def export_tflite():
    if not MODEL_PATH.exists():
        print(f"Error: {MODEL_PATH} not found.")
        return

    model = tf.keras.models.load_model(MODEL_PATH)

    # 1. Float32 Export
    print("Exporting Float32 TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    with open(TFLITE_PATH, "wb") as f:
        f.write(tflite_model)
    print(f"Saved: {TFLITE_PATH}")

    # 2. INT8 Quantization
    print("Exporting INT8 Quantized TFLite...")
    if not TRAIN_DATA_DIR.exists():
        print("Skipping INT8: Training data splits not found for representative dataset.")
        return

    converter_int8 = tf.lite.TFLiteConverter.from_keras_model(model)
    converter_int8.optimizations = [tf.lite.Optimize.DEFAULT]
    converter_int8.representative_dataset = representative_data_gen

    # Ensure full integer quantization
    converter_int8.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter_int8.inference_input_type = tf.uint8
    converter_int8.inference_output_type = tf.uint8

    tflite_model_int8 = converter_int8.convert()
    with open(QUANT_TFLITE_PATH, "wb") as f:
        f.write(tflite_model_int8)
    print(f"Saved: {QUANT_TFLITE_PATH}")

if __name__ == "__main__":
    export_tflite()
