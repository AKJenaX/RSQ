import tensorflow as tf
import numpy as np
from pathlib import Path
from sklearn.metrics import classification_report, confusion_matrix
import seaborn as sns
import matplotlib.pyplot as plt

# RSQ Target Classes
LABELS = ["NORMAL", "FLOOD", "FIRE_SMOKE", "COLLAPSED_STRUCTURE"]
TEST_DATA_DIR = Path("data/splits/test")
MODEL_PATH = Path("models/baseline_float32.keras")

def evaluate():
    if not MODEL_PATH.exists():
        print(f"Error: Model {MODEL_PATH} not found.")
        return

    model = tf.keras.models.load_model(MODEL_PATH)

    test_ds = tf.keras.utils.image_dataset_from_directory(
        TEST_DATA_DIR,
        image_size=(224, 224),
        batch_size=32,
        label_mode='categorical',
        shuffle=False
    )

    print("Running evaluation on held-out test set...")
    results = model.evaluate(test_ds)
    print(f"Test Accuracy: {results[1]:.4f}")

    # Detailed metrics
    y_true = []
    y_pred = []

    for x, y in test_ds:
        preds = model.predict(x)
        y_true.extend(np.argmax(y.numpy(), axis=1))
        y_pred.extend(np.argmax(preds, axis=1))

    print("\nClassification Report:")
    print(classification_report(y_true, y_pred, target_names=LABELS))

    # Confusion Matrix
    cm = confusion_matrix(y_true, y_pred)

    # Disaster -> NORMAL error rate
    # Disaster classes indices are 1, 2, 3
    disaster_samples = sum(cm[1:, :].sum(axis=1))
    disaster_as_normal = cm[1:, 0].sum()
    error_rate = (disaster_as_normal / disaster_samples) * 100 if disaster_samples > 0 else 0

    print(f"Disaster -> NORMAL Error Rate: {error_rate:.2f}%")

if __name__ == "__main__":
    evaluate()
