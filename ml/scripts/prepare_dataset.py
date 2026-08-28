import os
import shutil
import pandas as pd
from pathlib import Path

# Constants
RAW_DATA_DIR = Path("ml/data/raw")
PROCESSED_DATA_DIR = Path("ml/data/processed")
MANIFEST_PATH = PROCESSED_DATA_DIR / "manifest.csv"

MEDIC_RAW_DIR = RAW_DATA_DIR / "medic"
AIDER_RAW_DIR = RAW_DATA_DIR / "aider" / "AIDER"

RSQ_CLASSES = ["NORMAL", "FLOOD", "FIRE_SMOKE", "COLLAPSED_STRUCTURE"]
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png"}

def get_source_group(image_path_str):
    """
    Derive source_group from image_path's parent directory per RSQ requirements.
    Example: data/ASONAM17_Damage_Image_Dataset/building_damage/foo.jpg -> building_damage
    """
    path = Path(image_path_str)
    return path.parent.name

def process_medic():
    """
    Processes MEDIC dataset by enforcing strict class mapping and validation.
    """
    print("Processing MEDIC dataset...")
    tsvs = {
        "train": MEDIC_RAW_DIR / "MEDIC_train.tsv",
        "dev": MEDIC_RAW_DIR / "MEDIC_dev.tsv",
        "test": MEDIC_RAW_DIR / "MEDIC_test.tsv"
    }

    processed_sources = set()
    manifest_rows = []
    summary = {
        "accepted": 0,
        "rejected": 0,
        "missing": 0,
        "duplicates": 0,
        "by_class": {cls: 0 for cls in RSQ_CLASSES},
        "rejection_reasons": {}
    }

    def log_rejection(reason):
        summary["rejected"] += 1
        summary["rejection_reasons"][reason] = summary["rejection_reasons"].get(reason, 0) + 1

    # Sort TSV files for determinism
    for split_name in sorted(tsvs.keys()):
        tsv_path = tsvs[split_name]
        if not tsv_path.exists():
            print(f"Warning: MEDIC {split_name} TSV not found at {tsv_path}")
            continue

        df = pd.read_csv(tsv_path, sep='\t')
        for _, row in df.iterrows():
            image_path_rel = row['image_path']
            disaster_type = row['disaster_types']
            severity = row['damage_severity']
            source_group = get_source_group(image_path_rel)

            # 1. Duplicate Detection (Across all MEDIC split files)
            source_id = (str(image_path_rel), disaster_type)
            if source_id in processed_sources:
                summary["duplicates"] += 1
                log_rejection("duplicate_source_image")
                continue
            processed_sources.add(source_id)

            # 2. Mapping Logic
            rsq_class = None
            rejection_reason = None

            if disaster_type == "not_disaster":
                rsq_class = "NORMAL"
            elif disaster_type == "flood":
                rsq_class = "FLOOD"
            elif disaster_type == "fire":
                rsq_class = "FIRE_SMOKE"
            elif disaster_type == "earthquake":
                # Special rule for COLLAPSED_STRUCTURE:
                # ONLY building_damage directory AND severe damage
                if source_group == "building_damage":
                    if severity == "severe":
                        rsq_class = "COLLAPSED_STRUCTURE"
                    else:
                        rejection_reason = "non_severe_building_damage"
                else:
                    rejection_reason = "unsupported_medic_disaster_type"
            else:
                rejection_reason = "unsupported_medic_disaster_type"

            if not rsq_class:
                log_rejection(rejection_reason)
                continue

            # 3. Source Image Validation
            src_path = MEDIC_RAW_DIR / image_path_rel
            if not src_path.exists():
                summary["missing"] += 1
                log_rejection("missing_source_image")
                continue

            if src_path.suffix.lower() not in IMAGE_EXTENSIONS:
                log_rejection("unsupported_file_type")
                continue

            # 4. Secure Copy (Idempotent)
            dest_dir = PROCESSED_DATA_DIR / rsq_class
            dest_dir.mkdir(parents=True, exist_ok=True)

            # Use source_group in filename to prevent collisions
            dest_filename = f"medic_{source_group}_{src_path.name}"
            dest_path = dest_dir / dest_filename

            if not dest_path.exists():
                shutil.copy2(src_path, dest_path)

            # 5. Provenance Manifest Row
            manifest_rows.append({
                "output_path": str(dest_path),
                "rsq_class": rsq_class,
                "source_dataset": "MEDIC",
                "source_path": str(src_path),
                "source_group": source_group,
                "grouping_strategy": "MEDIC_EVENT",
                "original_split": split_name,
                "damage_severity": severity,
                "disaster_types": disaster_type
            })
            summary["accepted"] += 1
            summary["by_class"][rsq_class] += 1

    return manifest_rows, summary

def process_aider():
    """
    Processes AIDER dataset from the local folder structure.
    """
    print("Processing AIDER dataset...")
    mapping = {
        "collapsed_building": "COLLAPSED_STRUCTURE",
        "fire": "FIRE_SMOKE",
        "flooded_areas": "FLOOD",
        "normal": "NORMAL",
        "traffic_incident": "EXCLUDE"
    }

    manifest_rows = []
    summary = {
        "accepted": 0,
        "rejected": 0,
        "missing": 0,
        "duplicates": 0,
        "by_class": {cls: 0 for cls in RSQ_CLASSES},
        "rejection_reasons": {}
    }

    def log_rejection(reason):
        summary["rejected"] += 1
        summary["rejection_reasons"][reason] = summary["rejection_reasons"].get(reason, 0) + 1

    if not AIDER_RAW_DIR.exists():
        print(f"Warning: AIDER root directory not found at {AIDER_RAW_DIR}")
        return manifest_rows, summary

    # Sort folders for determinism
    for folder_name in sorted(mapping.keys()):
        rsq_class = mapping[folder_name]
        src_dir = AIDER_RAW_DIR / folder_name

        if not src_dir.exists():
            print(f"Warning: AIDER folder {folder_name} not found.")
            continue

        if rsq_class == "EXCLUDE":
            # Explicitly log rejected images for excluded classes
            for img in sorted(src_dir.iterdir()):
                if img.is_file():
                    log_rejection("excluded_aider_class")
            continue

        # Sort files for determinism
        for img_path in sorted(src_dir.iterdir()):
            if not img_path.is_file():
                continue

            if img_path.suffix.lower() not in IMAGE_EXTENSIONS:
                log_rejection("unsupported_file_type")
                continue

            # 1. Secure Copy
            dest_dir = PROCESSED_DATA_DIR / rsq_class
            dest_dir.mkdir(parents=True, exist_ok=True)

            dest_filename = f"aider_{img_path.name}"
            dest_path = dest_dir / dest_filename

            if not dest_path.exists():
                shutil.copy2(img_path, dest_path)

            # 2. Add to Manifest
            # For AIDER, source_group is image-unique to allow individual splitting
            manifest_rows.append({
                "output_path": str(dest_path),
                "rsq_class": rsq_class,
                "source_dataset": "AIDER",
                "source_path": str(img_path),
                "source_group": img_path.stem,
                "grouping_strategy": "AIDER_CLASS",
                "original_split": "N/A",
                "damage_severity": "N/A",
                "disaster_types": folder_name
            })
            summary["accepted"] += 1
            summary["by_class"][rsq_class] += 1

    return manifest_rows, summary

def main():
    PROCESSED_DATA_DIR.mkdir(parents=True, exist_ok=True)

    medic_rows, medic_summary = process_medic()
    aider_rows, aider_summary = process_aider()

    all_rows = medic_rows + aider_rows
    if all_rows:
        pd.DataFrame(all_rows).to_csv(MANIFEST_PATH, index=False)

    print("\n" + "="*60)
    print("RSQ DATASET PREPARATION SUMMARY")
    print("="*60)

    print(f"\nMEDIC Metrics:")
    print(f"  - Accepted:   {medic_summary['accepted']}")
    print(f"  - Rejected:   {medic_summary['rejected']}")
    print(f"  - Missing:    {medic_summary['missing']}")
    print(f"  - Duplicates: {medic_summary['duplicates']}")
    for cls in RSQ_CLASSES:
        print(f"    * {cls.ljust(20)}: {medic_summary['by_class'][cls]}")
    for reason, count in sorted(medic_summary['rejection_reasons'].items()):
        print(f"    * REJECTED: {reason.ljust(30)}: {count}")

    print(f"\nAIDER Metrics:")
    print(f"  - Accepted:   {aider_summary['accepted']}")
    print(f"  - Rejected:   {aider_summary['rejected']}")
    for cls in RSQ_CLASSES:
        print(f"    * {cls.ljust(20)}: {aider_summary['by_class'][cls]}")
    for reason, count in sorted(aider_summary['rejection_reasons'].items()):
        print(f"    * REJECTED: {reason.ljust(30)}: {count}")

    print(f"\nFinal Distribution by Class (Total):")
    for cls in RSQ_CLASSES:
        m_count = medic_summary['by_class'][cls]
        a_count = aider_summary['by_class'][cls]
        print(f"  {cls.ljust(20)}: {m_count + a_count} (MEDIC: {m_count}, AIDER: {a_count})")

    print(f"\nTotal Processed Rows: {len(all_rows)}")
    print(f"Provenance Manifest:  {MANIFEST_PATH}")

if __name__ == "__main__":
    main()
