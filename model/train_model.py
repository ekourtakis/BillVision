from roboflow import Roboflow
from dotenv import load_dotenv
from ultralytics import YOLO
import os
import sys

# --- Configuration ---
MODEL_CONFIG = "yolov8s.pt"
EPOCHS = 100
IMAGE_SIZE = 640
BATCH_SIZE = 16
DEVICE = None
WORKERS = 8

# --- Path Configuration (Relative to this script's location) ---
CUSTOM_YAML_PATH = "billvision_config.yaml"
EXPECTED_DATASET_DIR = "USD_Total-1"
PROJECT_OUTPUT_DIR = "runs" # The main folder for all runs (will be created inside 'model/')
EXPERIMENT_NAME = "dollar_detector_custom_yaml" # Subfolder within PROJECT_OUTPUT_DIR/detect/

# --- Roboflow Config ---
ROBOFLOW_API_KEY = os.getenv("ROBOFLOW_API_KEY")
ROBOFLOW_WORKSPACE = "jm-cbkgb"
ROBOFLOW_PROJECT = "usd_total"
ROBOFLOW_VERSION = 1
# --- ---

def main():
    # --- Check if Custom YAML exists ---
    if not os.path.exists(CUSTOM_YAML_PATH):
        print(f"FATAL ERROR: Custom configuration file '{CUSTOM_YAML_PATH}' not found.")
        print(f"Please ensure '{CUSTOM_YAML_PATH}' exists in the same directory as the script.")
        sys.exit(1)

    # --- Check if Dataset needs downloading ---
    if not os.path.exists(EXPECTED_DATASET_DIR) or not os.path.isdir(EXPECTED_DATASET_DIR):
        print(f"Dataset directory '{EXPECTED_DATASET_DIR}' not found.")
        print("--- Attempting to download data set from Roboflow ---")
        try:
            rf = Roboflow(api_key=ROBOFLOW_API_KEY)
            project = rf.workspace(ROBOFLOW_WORKSPACE).project(ROBOFLOW_PROJECT)
            version = project.version(ROBOFLOW_VERSION)
            dataset = version.download("yolov8")
            print(f"Dataset downloaded to: {dataset.location}")
            if not os.path.exists(EXPECTED_DATASET_DIR) or not os.path.isdir(EXPECTED_DATASET_DIR):
                 print(f"FATAL ERROR: Roboflow download did not create expected directory '{EXPECTED_DATASET_DIR}'.")
                 print(f"Downloaded location reported: {dataset.location}")
                 sys.exit(1)
            print("--- Dataset download successful ---")
        except Exception as e:
            print(f"FATAL ERROR during Roboflow download: {e}")
            sys.exit(1)
    else:
        print(f"Dataset directory '{EXPECTED_DATASET_DIR}' found locally. Skipping download.")

    # --- Get Absolute Path for YAML ---
    absolute_yaml_path = os.path.abspath(CUSTOM_YAML_PATH)

    print("\n--- Starting YOLOv8 Training ---")
    print(f"Model: {MODEL_CONFIG}")
    print(f"Using Custom Dataset YAML: {absolute_yaml_path}")
    # --- Specify Output Location ---
    print(f"Project Output Directory (relative to script): ./{PROJECT_OUTPUT_DIR}")
    print(f"Experiment Name (sub-directory): {EXPERIMENT_NAME}")
    # --- ---
    print(f"Epochs: {EPOCHS}")
    print(f"Image Size: {IMAGE_SIZE}")
    print(f"Batch Size: {BATCH_SIZE}")
    print(f"Device: {'Auto-detect' if DEVICE is None else DEVICE}")
    print(f"Workers: {WORKERS}")
    print("-" * 30)

    # 1. Load the model
    model = YOLO(MODEL_CONFIG)

    # 2. Train the model
    try:
        results = model.train(
            data=absolute_yaml_path,
            epochs=EPOCHS,
            imgsz=IMAGE_SIZE,
            batch=BATCH_SIZE,
            # --- ADD/MODIFY project and name arguments ---
            project=PROJECT_OUTPUT_DIR, # Specify the main output directory
            name=EXPERIMENT_NAME,       # Specify the specific run's sub-directory
            # --- ---
            device=DEVICE,
            workers=WORKERS,
            exist_ok=False # Set to True if you want to allow overwriting existing experiment folders
        )
        print("-" * 30)
        # Note: The final save directory path is usually runs/detect/experiment_name
        # You can access it via the trainer object if needed, but it prints logs anyway.
        print("Training finished successfully!")
        print(f"Results saved within the '{PROJECT_OUTPUT_DIR}' directory.")


    except Exception as e:
        print("-" * 30)
        # Basic error handling remains the same
        if "Dataset" in str(e) and "images not found" in str(e):
             print(f"An error occurred during training, likely path related: {e}")
             print("\nPossible causes:")
             print(f" - YOLO could not find images based on paths in your custom YAML: '{absolute_yaml_path}'")
             print(f" - Check the 'train:', 'val:', 'test:' paths inside that YAML file (e.g., '{EXPECTED_DATASET_DIR}/train/images').")
        else:
            print(f"An error occurred during training: {e}")

if __name__ == '__main__':
    main()
