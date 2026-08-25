# RSQ Disaster Classifier - Model Card

## Model Information
- **Name**: RSQ Disaster Image Classifier (Baseline)
- **Architecture**: EfficientNetV2B0 (Transfer Learning)
- **Input Size**: 224 x 224 x 3 (RGB)
- **Framework**: TensorFlow 2.16.1 / LiteRT

## Dataset
- **Primary**: MEDIC (Ground-level / Social Media)
- **Supplemental**: AIDER (Aerial / UAV)
- **Total Images**: NOT YET MEASURED.
- **Classes**:
  - 0: NORMAL
  - 1: FLOOD
  - 2: FIRE_SMOKE
  - 3: COLLAPSED_STRUCTURE

## Training Configuration
- **Optimizer**: Adam
- **Initial LR**: 1e-4
- **Loss**: Categorical Cross Entropy
- **Augmentation**: Flip, Rotation, Contrast jitter

## Evaluation Metrics (Held-out Test Set)
- **Overall Accuracy**: NOT YET MEASURED.
- **Macro F1**: NOT YET MEASURED.
- **Disaster -> NORMAL Error Rate**: NOT YET MEASURED.

| Class | Precision | Recall | F1-Score |
| :--- | :--- | :--- | :--- |
| NORMAL | - | - | - |
| FLOOD | - | - | - |
| FIRE_SMOKE | - | - | - |
| COLLAPSED_STRUCT | - | - | - |

## Model Contract
- **Input Tensor**: `[1, 224, 224, 3]` (float32)
- **Normalization**: `(x / 255.0)` (Baseline target)
- **Output Tensor**: `[1, 4]` (float32, Softmax)

## Quantization
- **Type**: Float32 (INT8 TODO)
- **Size**: NOT YET MEASURED.

## Limitations
- Performance on night-time imagery not yet verified.
- Primarily trained on clear-weather disaster scenes.
- Potential bias towards specific geographic regions present in MEDIC.
