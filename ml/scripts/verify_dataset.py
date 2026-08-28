import os
import pandas as pd
from pathlib import Path

MANIFEST_PATH = Path("data/processed_manifest.csv")
RAW_DATA_DIR = Path("data/raw")
PROCESSED_DATA_DIR = Path("data/processed")
SPLIT_DATA_DIR = Path("data/splits")

def verify():
    print("=== RSQ ML PIPELINE VERIFICATION ===\n")

    # 1. Check Raw Data
    print("1. Raw Data Status:")
    medic_exists = (RAW_DATA_DIR / "medic" / "medic_data.tsv").exists()
    aider_exists = (RAW_DATA_DIR / "aider").exists()

    print(f"   MEDIC: {'FOUND' if medic_exists else 'NOT FOUND'}")
    print(f"   AIDER: {'FOUND' if aider_exists else 'NOT FOUND'}")

    if not (medic_exists or aider_exists):
        print("\nBLOCKER: Raw datasets missing. Training cannot proceed.")
        print("Required Placement:")
        print(f"  - {RAW_DATA_DIR}/medic/medic_data.tsv")
        print(f"  - {RAW_DATA_DIR}/aider/")
        return

    # 2. Check Processed Manifest
    if MANIFEST_PATH.exists():
        df = pd.read_csv(MANIFEST_PATH)
        print("\n2. Processed Manifest:")
        print(f"   Total entries: {len(df)}")
        print(f"   Accepted: {len(df[df.review_status == 'accepted'])}")
        print(f"   Requires Manual Review: {len(df[df.review_status == 'requires_review'])}")
    else:
        print("\n2. Processed Manifest: NOT FOUND. Run prepare_dataset.py.")

    # 3. Check Split Counts
    if SPLIT_DATA_DIR.exists():
        print("\n3. Split Distribution:")
        for split in ["train", "val", "test"]:
            total = 0
            for cls in ["NORMAL", "FLOOD", "FIRE_SMOKE", "COLLAPSED_STRUCTURE"]:
                count = len(list((SPLIT_DATA_DIR / split / cls).glob("*")))
                total += count
            print(f"   {split.capitalize()}: {total} images")
    else:
        print("\n3. Splits: NOT FOUND. Run split_dataset.py.")

    # 4. Preprocessing Contract
    print("\n4. Preprocessing Contract:")
    print("   Input: 224 x 224 RGB")
    print("   Scaling: Rescaling(1./255) embedded in model")
    print("   Backbone: EfficientNetV2B0 (ImageNet Pretrained)")

if __name__ == "__main__":
    verify()
