# RSQ Disaster Image Classifier Training Pipeline

This workspace contains the tools to prepare, train, and export the real-time disaster image classifier for the RSQ Android application.

## Dataset Acquisition

To train the model, you must acquire the following datasets officially. **Do not redistribute raw image files.**

### 1. MEDIC (Primary)
- **Source**: [CrisisNLP MEDIC](https://crisisnlp.qcri.org/medic/)
- **License**: CC BY-NC-SA 4.0
- **Focus**: Social media ground-level imagery.

### 2. AIDER (Supplemental)
- **Source**: [AIDER GitHub](https://github.com/ckyrkou/AIDER/)
- **License**: Academic Use Only
- **Focus**: Aerial/UAV imagery for structural collapse.

## Setup

1. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

2. Download and extract datasets into `ml/data/raw/`:
   - `ml/data/raw/medic/`
   - `ml/data/raw/aider/`

3. Run the preparation script:
   ```bash
   python scripts/prepare_dataset.py
   ```

4. Split the data (event-aware where possible):
   ```bash
   python scripts/split_dataset.py
   ```

## Training

Run the training script:
```bash
python training/train.py
```

The script will save the best Float32 model to `models/baseline_float32.keras`.

## Exporting to LiteRT

Convert the model to TFLite/LiteRT format with INT8 quantization:
```bash
python export/export_tflite.py
```

The final artifact will be placed at `models/disaster_classifier.tflite`.
