from roboflow import Roboflow
from dotenv import load_dotenv
from ultralytics import YOLO
import os
import sys
import shutil

load_dotenv()

# --- Configuration ---
MODEL_CONFIG = "yolov8s.pt"
EPOCHS = 120
IMAGE_SIZE = 640
BATCH_SIZE = 8
DEVICE = None
WORKERS = 8

# --- TFLite Export Configuration ---
EXPORT_TFLITE = True
TFLITE_INT8 = True

# --- Roboflow Config ---
ROBOFLOW_API_KEY = os.getenv("ROBOFLOW_API_KEY")
ROBOFLOW_WORKSPACE = "scratch-enrjz"
ROBOFLOW_PROJECT = "usd_total-ns6qv"
ROBOFLOW_VERSION = 2

# --- Path Configuration ---
CUSTOM_YAML_PATH = "billvision_config.yaml"
EXPECTED_DATASET_DIR = "USD_Total-" + str(ROBOFLOW_VERSION)
PROJECT_OUTPUT_DIR = "runs" 
EXPERIMENT_NAME = "dollar_detector_custom_yaml"

def check_tflite_dependencies():
    """Checks if TensorFlow and TFLite runtime are likely installed."""
    try:
        import tensorflow
        print("TensorFlow found (required for TFLite export).")
        return True
    except ImportError:
        print("WARNING: TensorFlow not found. TFLite export requires TensorFlow.")
        print("Please install it: pip install tensorflow")
        return False

def main():
    # --- Check Roboflow API Key ---
    if not ROBOFLOW_API_KEY:
        print("FATAL ERROR: Roboflow API key not found. Set ROBOFLOW_API_KEY environment variable or in .env file.")
        sys.exit(1)

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

    # --- Training ---
    training_successful = False
    best_model_path = None
    model = None

    try:
        print("\n--- Starting YOLOv8 Training ---")
        print(f"Model: {MODEL_CONFIG}")
        print(f"Using Custom Dataset YAML: {absolute_yaml_path}")
        print(f"Project Output Directory (relative to script): ./{PROJECT_OUTPUT_DIR}")
        print(f"Experiment Name (sub-directory): {EXPERIMENT_NAME}")
        print(f"Epochs: {EPOCHS}")
        print(f"Image Size: {IMAGE_SIZE}")
        print(f"Batch Size: {BATCH_SIZE}")
        print(f"Device: {'Auto-detect' if DEVICE is None else DEVICE}")
        print(f"Workers: {WORKERS}")
        print("-" * 30)

        # load the base model
        model = YOLO(MODEL_CONFIG)

        # train the model
        results = model.train(
            data=absolute_yaml_path,
            epochs=EPOCHS,
            imgsz=IMAGE_SIZE,
            batch=BATCH_SIZE,
            project=PROJECT_OUTPUT_DIR,
            name=EXPERIMENT_NAME,
            device=DEVICE,
            workers=WORKERS,
            exist_ok=True
        )
        print("-" * 30)
        print("Training finished successfully!")

        save_dir = results.save_dir

        print(f"Actual save directory reported by Ultralytics: {save_dir}")

        best_model_path = os.path.join(save_dir, 'weights', 'best.pt')
        print(f"Best model path: {best_model_path}")

        if os.path.exists(best_model_path):
            print(f"Best model saved at: {best_model_path}")
            training_successful = True
        else:
            print(f"WARNING: Training finished, but best model not found at expected location: {best_model_path}")
            print("Skipping TFLite export.")

    except Exception as e:
        print("-" * 30)
        print(f"An error occurred during training: {e}")
        if "Dataset" in str(e) and "images not found" in str(e):
             print("\nPossible causes:")
             print(f" - YOLO could not find images based on paths in your custom YAML: '{absolute_yaml_path}'")
             print(f" - Check the 'train:', 'val:', 'test:' paths inside that YAML file (e.g., '{EXPECTED_DATASET_DIR}/train/images').")
        sys.exit(1) # training failed


    # --- TFLite Export ---
    if training_successful and EXPORT_TFLITE:
        print("\n--- Starting TFLite Export ---")

        if not check_tflite_dependencies():
             print("Cannot perform TFLite export due to missing dependencies. Please install TensorFlow.")
             sys.exit(1)

        try:
            # load the best trained model
            export_model = YOLO(best_model_path)

            print(f"Exporting model from: {best_model_path}")
            print(f"Format: tflite")
            print(f"Image Size: {IMAGE_SIZE}")
            print(f"INT8 Quantization: {TFLITE_INT8}")
            if TFLITE_INT8:
                print(f"Using calibration data from: {absolute_yaml_path}")

            # export
            tflite_output_path = export_model.export(
                format="tflite",
                imgsz=IMAGE_SIZE,
                int8=False,
                half=True,
            )
            print("-" * 30)
            print(f"TFLite export successful!")
            print(f"Model saved to: {tflite_output_path}") # export returns path

            target_path = "../android-app/app/src/main/assets/usd_detector.tflite"

            if os.path.exists(tflite_output_path):
                print(f"Moving exported model to final destination: {target_path}")
                shutil.move(tflite_output_path, target_path)
                print(f"TFLite model successfully moved.")
            else:
                print(f"Error moving TFLite model from {tflite_output_path} to {target_path}")

        except Exception as e:
            print("-" * 30)
            print(f"An error occurred during TFLite export: {e}")

    elif not EXPORT_TFLITE:
        print("\nTFLite export is disabled in the script configuration.")

if __name__ == '__main__':
    main()
