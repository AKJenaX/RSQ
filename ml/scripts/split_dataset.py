import os
import shutil
import pandas as pd
import numpy as np
import random
from pathlib import Path

# Configuration
SEED = 42
RATIOS = {"train": 0.70, "val": 0.15, "test": 0.15}
PROCESSED_DATA_DIR = Path("ml/data/processed")
SPLIT_DATA_DIR = Path("ml/data/splits")
MANIFEST_PATH = PROCESSED_DATA_DIR / "manifest.csv"
SPLIT_MANIFEST_PATH = SPLIT_DATA_DIR / "split_manifest.csv"

RSQ_CLASSES = ["NORMAL", "FLOOD", "FIRE_SMOKE", "COLLAPSED_STRUCTURE"]

def validate_manifest_integrity(df):
    """
    Performs strict validation of the manifest before splitting.
    """
    print("Validating manifest integrity...")
    required_cols = [
        "output_path", "rsq_class", "source_dataset", "source_path",
        "source_group", "grouping_strategy"
    ]
    for col in required_cols:
        if col not in df.columns:
            raise ValueError(f"Missing required column: {col}")

    # 1. Source Path Uniqueness (Hard Requirement)
    if not df["source_path"].is_unique:
        duplicates = df[df["source_path"].duplicated()]["source_path"].unique().tolist()
        raise RuntimeError(f"SOURCE DUPLICATION FAILURE: Duplicate source_path values found: {duplicates}")

    # 2. Output Path Uniqueness
    if not df["output_path"].is_unique:
        duplicates = df[df["output_path"].duplicated()]["output_path"].unique().tolist()
        raise RuntimeError(f"MANIFEST FAILURE: Duplicate output_path values found: {duplicates}")

    # 3. Physical File Existence
    for path in df["output_path"]:
        if not os.path.exists(path):
            raise FileNotFoundError(f"FILE EXISTENCE FAILURE: Processed file not found at: {path}")

    # 4. Class Validity
    invalid_classes = df[~df["rsq_class"].isin(RSQ_CLASSES)]["rsq_class"].unique()
    if len(invalid_classes) > 0:
        raise ValueError(f"INVALID CLASS FAILURE: Unsupported RSQ classes found: {invalid_classes}")

    print("MANIFEST INTEGRITY: PASS")

def get_internal_group_id(row):
    """
    Constructs a normalized unique internal group ID.

    AIDER: AIDER_ + source_path (normalized posix). Each image is independent.

    MEDIC (ASONAM17 building_damage): MEDIC_ + source_path (normalized posix).
    Intentionally image-level grouped because files are independently numbered
    and the parent directory is too coarse for effective splitting (leading to poor
    distribution of the COLLAPSED_STRUCTURE class).

    MEDIC (Other): MEDIC_ + parent directory (normalized posix).
    Standard event-level grouping to prevent leakage.
    """
    source_path = Path(row['source_path'])
    normalized_path = source_path.as_posix()

    if row['source_dataset'] == 'AIDER':
        return 'AIDER_' + normalized_path

    # MEDIC Specific logic
    if "building_damage" in normalized_path:
        # ASONAM17 building_damage images are independent
        return 'MEDIC_' + normalized_path
    else:
        # Standard MEDIC event grouping
        return 'MEDIC_' + source_path.parent.as_posix()

def assign_splits_by_image_count(df):
    """
    Assigns groups to splits approximating 70/15/15 based on IMAGE COUNTS.
    Maintains the hard constraint: MEDIC groups cannot cross splits.
    """
    random.seed(SEED)

    # 1. Create normalized unique group IDs
    df['internal_group_id'] = df.apply(get_internal_group_id, axis=1)

    group_assignments = {}

    # 2. Process class by class to maintain representation
    for cls in RSQ_CLASSES:
        # Get groups primarily belonging to this class that aren't assigned yet
        cls_df = df[(df['rsq_class'] == cls) & (~df['internal_group_id'].isin(group_assignments.keys()))]

        if cls_df.empty:
            continue

        # Get group metadata
        group_counts = cls_df.groupby('internal_group_id').size().reset_index(name='img_count')
        group_list = group_counts.to_dict('records')

        # Sort for determinism
        group_list.sort(key=lambda x: x['internal_group_id'])
        random.shuffle(group_list)

        # Targets for this class
        total_cls_imgs = group_counts['img_count'].sum()
        targets = {s: total_cls_imgs * r for s, r in RATIOS.items()}
        current = {s: 0 for s in RATIOS.keys()}

        # Greedy assignment
        for g in group_list:
            # Assign to split with largest relative remaining capacity
            split = min(current.keys(), key=lambda s: current[s] / targets[s] if targets[s] > 0 else 0)
            group_assignments[g['internal_group_id']] = split
            current[split] += g['img_count']

    return group_assignments

def verify_determinism(df):
    """
    Verifies that the assignment algorithm is 100% deterministic with fixed SEED.
    """
    print("Verifying determinism...")
    assignment_a = assign_splits_by_image_count(df.copy())
    assignment_b = assign_splits_by_image_count(df.copy())

    if assignment_a != assignment_b:
        raise RuntimeError("DETERMINISM FAILURE: identical input produced different split assignments.")

    print("DETERMINISM: PASS")

def verify_split_validity(df):
    """
    Hard verification of split logic and constraints.
    """
    print("Verifying split validity...")

    # 1. Assignment Completeness & Validity
    if df['split'].isnull().any():
        raise RuntimeError("SPLIT ASSIGNMENT FAILURE: Null split values found.")

    actual_splits = set(df['split'].unique())
    if not actual_splits <= {"train", "val", "test"}:
        raise RuntimeError(f"SPLIT ASSIGNMENT FAILURE: Invalid split labels found: {actual_splits}")

    # 2. MEDIC Group Leakage
    medic_df = df[df['source_dataset'] == 'MEDIC']
    if not medic_df.empty:
        group_split_counts = medic_df.groupby('internal_group_id')['split'].nunique()
        leaking_groups = group_split_counts[group_split_counts > 1].index.tolist()
        if leaking_groups:
            raise RuntimeError(f"MEDIC GROUP LEAKAGE FAILURE: Groups spanning multiple splits: {leaking_groups}")

    # 3. Source Path Uniqueness across splits
    source_split_counts = df.groupby('source_path')['split'].nunique()
    if source_split_counts.max() > 1:
        leaking_sources = source_split_counts[source_split_counts > 1].index.tolist()
        raise RuntimeError(f"SOURCE DUPLICATION FAILURE: Source images appearing in multiple splits: {leaking_sources}")

    print("MEDIC GROUP LEAKAGE: PASS")
    print("SOURCE DUPLICATION: PASS")
    print("SPLIT ASSIGNMENT: PASS")

def main():
    if not MANIFEST_PATH.exists():
        print(f"Error: {MANIFEST_PATH} not found. Run prepare_dataset.py first.")
        return

    df = pd.read_csv(MANIFEST_PATH)
    validate_manifest_integrity(df)

    # 1. Determinism Check
    verify_determinism(df)

    # 2. Assign splits
    group_assignments = assign_splits_by_image_count(df)
    df['split'] = df['internal_group_id'].map(group_assignments)

    # 3. Logic Verification
    verify_split_validity(df)

    # 4. Physical Copy
    if SPLIT_DATA_DIR.exists():
        shutil.rmtree(SPLIT_DATA_DIR)

    for s in ["train", "val", "test"]:
        for c in RSQ_CLASSES:
            (SPLIT_DATA_DIR / s / c).mkdir(parents=True, exist_ok=True)

    print("Copying files to split directories...")
    for _, row in df.iterrows():
        dest_path = SPLIT_DATA_DIR / row['split'] / row['rsq_class'] / Path(row['output_path']).name
        shutil.copy2(row['output_path'], dest_path)

    # 5. Final Manifest Save
    out_cols = [c for c in df.columns if c != 'internal_group_id']
    df[out_cols].to_csv(SPLIT_MANIFEST_PATH, index=False)

    # 6. Per-row Physical Verification
    print("Verifying physical output per row...")
    for _, row in df.iterrows():
        expected_path = SPLIT_DATA_DIR / row['split'] / row['rsq_class'] / Path(row['output_path']).name
        if not expected_path.exists():
            raise RuntimeError(f"FILE EXISTENCE FAILURE: Expected file missing at {expected_path}")

    # Count images in split/class directories (excludes root files like split_manifest.csv)
    image_count = sum(len(list((SPLIT_DATA_DIR / s / c).glob("*")))
                     for s in ["train", "val", "test"]
                     for c in RSQ_CLASSES)

    if image_count != len(df):
         raise RuntimeError(f"FILE EXISTENCE FAILURE: Physical image count ({image_count}) does not match manifest count ({len(df)}).")

    print("FILE EXISTENCE: PASS")

    # 7. Final Report
    print("\nRSQ DATASET SPLIT SUMMARY")
    print("="*60)
    print(f"Total images: {len(df)}")
    print(f"Random Seed:  {SEED}")

    print("\nTARGET SPLITS")
    for s, r in RATIOS.items():
        print(f"  {s.capitalize().ljust(8)}: {r*100:.1f}%")

    print("\nACTUAL SPLITS")
    for s in ["train", "val", "test"]:
        s_df = df[df['split'] == s]
        pct = (len(s_df) / len(df)) * 100
        print(f"  {s.capitalize().ljust(8)}: {pct:5.1f}% ({len(s_df)})")

        # Check for large deviation
        target = RATIOS[s] * 100
        if abs(pct - target) > 5:
            print(f"    WARNING: {s.upper()} distribution is {pct:.1f}%, target {target:.1f}%.")

    print("\nPER-CLASS DISTRIBUTION")
    for cls in RSQ_CLASSES:
        cls_total = len(df[df['rsq_class'] == cls])
        print(f"\n{cls}:")
        for s in ["train", "val", "test"]:
            s_cls_count = len(df[(df['split'] == s) & (df['rsq_class'] == cls)])
            s_cls_pct = (s_cls_count / cls_total * 100) if cls_total > 0 else 0
            print(f"  {s.capitalize().ljust(8)}: {s_cls_pct:5.1f}% ({s_cls_count})")

    # Final summary check
    for c in RSQ_CLASSES:
        splits_present = df[df['rsq_class'] == c]['split'].nunique()
        if splits_present < 3:
            print(f"\nWARNING: Class {c} is not present in all three splits (Count: {splits_present}/3).")

    print("\nFINAL ASSERTIONS")
    print("----------------")
    print("MANIFEST INTEGRITY: PASS")
    print("DETERMINISM: PASS")
    print("MEDIC GROUP LEAKAGE: PASS")
    print("SOURCE DUPLICATION: PASS")
    print("SPLIT ASSIGNMENT: PASS")
    print("FILE EXISTENCE: PASS")

if __name__ == "__main__":
    main()
