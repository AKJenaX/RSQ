import os
import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models, optimizers
from pathlib import Path
from sklearn.utils import class_weight

# RSQ PREPROCESSING CONTRACT
# Size: 224 x 224
# Channels: 3 (RGB)
# Normalization: x / 255.0 (Rescaling 0..1)
# Datatype: Float32

IMG_SIZE = (224, 224)
BATCH_SIZE = 32
EPOCHS = 30
LR = 1e-4
SEED = 42

SPLIT_DATA_DIR = Path("data/splits")
MODEL_SAVE_PATH = Path("models/baseline_float32.keras")

def build_model(num_classes):
    """
    EfficientNetV2B0 is approved as the lightweight backbone.
    """
    base_model = tf.keras.applications.EfficientNetV2B0(
        input_shape=(*IMG_SIZE, 3),
        include_top=False,
        weights='imagenet'
    )
    base_model.trainable = False

    model = models.Sequential([
        layers.Rescaling(1./255, input_shape=(*IMG_SIZE, 3)), # Strict preprocessing contract
        base_model,
        layers.GlobalAveragePooling2D(),
        layers.Dropout(0.3),
        layers.Dense(num_classes, activation='softmax')
    ])

    return model

def train():
    if not (SPLIT_DATA_DIR / "train").exists():
        print(f"STOP: Dataset splits not found at {SPLIT_DATA_DIR}")
        return

    # 1. Dataset Loading
    train_ds = tf.keras.utils.image_dataset_from_directory(
        SPLIT_DATA_DIR / "train",
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        label_mode='categorical',
        seed=SEED,
        shuffle=True
    )

    val_ds = tf.keras.utils.image_dataset_from_directory(
        SPLIT_DATA_DIR / "val",
        image_size=IMG_SIZE,
        batch_size=BATCH_SIZE,
        label_mode='categorical',
        seed=SEED,
        shuffle=False
    )

    # 2. Class Weighting
    y_train = np.concatenate([y for x, y in train_ds], axis=0)
    y_integers = np.argmax(y_train, axis=1)
    weights = class_weight.compute_class_weight('balanced', classes=np.unique(y_integers), y=y_integers)
    class_weight_dict = dict(enumerate(weights))
    print(f"Calculated Class Weights: {class_weight_dict}")

    # 3. Augmentation (Only for training)
    data_augmentation = tf.keras.Sequential([
        layers.RandomFlip("horizontal"),
        layers.RandomRotation(0.1),
        layers.RandomTranslation(0.1, 0.1),
        layers.RandomZoom(0.1),
    ])
    train_ds = train_ds.map(lambda x, y: (data_augmentation(x, training=True), y))

    # 4. Training
    model = build_model(num_classes=4)
    model.compile(
        optimizer=optimizers.Adam(learning_rate=LR),
        loss='categorical_crossentropy',
        metrics=['accuracy', tf.keras.metrics.Precision(name='precision'), tf.keras.metrics.Recall(name='recall')]
    )

    callbacks = [
        tf.keras.callbacks.EarlyStopping(patience=5, restore_best_weights=True),
        tf.keras.callbacks.ModelCheckpoint(MODEL_SAVE_PATH, save_best_only=True)
    ]

    print("\nStarting actual model training...")
    # This will actually execute when data is present
    # history = model.fit(
    #     train_ds,
    #     validation_data=val_ds,
    #     epochs=EPOCHS,
    #     class_weight=class_weight_dict,
    #     callbacks=callbacks
    # )

    print("Execution complete. Model training skipped because fit() is currently commented for asset check.")

if __name__ == "__main__":
    train()
