import tensorflow as tf
import numpy as np
from pathlib import Path

TFLITE_PATH = Path("models/disaster_classifier.tflite")

def verify_tflite():
    if not TFLITE_PATH.exists():
        print(f"Error: {TFLITE_PATH} not found.")
        return

    # Load the TFLite model and allocate tensors
    interpreter = tf.lite.Interpreter(model_path=str(TFLITE_PATH))
    interpreter.allocate_tensors()

    # Get input and output details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print("=== TFLITE MODEL VERIFICATION ===\n")
    print(f"Input Shape: {input_details[0]['shape']}")
    print(f"Input Type: {input_details[0]['dtype']}")
    print(f"Output Shape: {output_details[0]['shape']}")
    print(f"Output Type: {output_details[0]['dtype']}")

    # Test with dummy data
    input_shape = input_details[0]['shape']
    dummy_input = np.random.random_sample(input_shape).astype(np.float32)

    interpreter.set_tensor(input_details[0]['index'], dummy_input)
    interpreter.invoke()

    output_data = interpreter.get_tensor(output_details[0]['index'])
    print(f"\nDummy Inference Result (Probabilities): {output_data}")
    print(f"Sum of probabilities: {np.sum(output_data):.4f}")

    if np.abs(np.sum(output_data) - 1.0) < 0.001:
        print("Success: Output is a valid softmax distribution.")
    else:
        print("Warning: Output is NOT a valid softmax distribution.")

if __name__ == "__main__":
    verify_tflite()
