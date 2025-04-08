import cv2
from ultralytics import YOLO
import os

# --- Configuration ---
MODEL_PATH = "/home/manny/developer/BillVision/runs/detect/dollar_detector_roboflow6/weights/best.pt"
IMAGE_PATH = "three_20s.jpg" # <--- CHANGE THIS TO YOUR IMAGE
OUTPUT_DIR = "inference_output"
CONFIDENCE_THRESHOLD = 0.5 # Only show detections with confidence >= 50%
# --- ---

# Create output directory if it doesn't exist
os.makedirs(OUTPUT_DIR, exist_ok=True)
output_image_name = os.path.basename(IMAGE_PATH)
output_image_path = os.path.join(OUTPUT_DIR, f"predicted_{output_image_name}")

# 1. Load the trained YOLOv8 model
try:
    print(f"Loading model from {MODEL_PATH}...")
    model = YOLO(MODEL_PATH)
    print("Model loaded successfully.")
except Exception as e:
    print(f"Error loading model: {e}")
    exit()

# 2. Load the image using OpenCV
print(f"Loading image from {IMAGE_PATH}...")
img = cv2.imread(IMAGE_PATH)

if img is None:
    print(f"Error: Could not read image file at {IMAGE_PATH}")
    exit()

print("Image loaded successfully.")

# 3. Run inference
print("Running inference...")
results = model.predict(img, verbose=False) # Use verbose=False for cleaner output
print("Inference complete.")

# 4. Process results and draw bounding boxes
# results is a list, typically with one element for single image inference
result = results[0] # Get the results object for the first (and only) image

# Load class names from the model
class_names = result.names

print(f"Found {len(result.boxes)} potential detections.")

for box in result.boxes:
    # Extract data for each box
    x1, y1, x2, y2 = map(int, box.xyxy[0]) # Top-left and bottom-right coordinates
    confidence = float(box.conf[0])
    class_id = int(box.cls[0])
    class_name = class_names[class_id]

    print("Class: " + class_name)

    # Filter detections by confidence threshold
    if confidence >= CONFIDENCE_THRESHOLD:
        print(f"  Detected: {class_name} (Confidence: {confidence:.2f}) at [{x1}, {y1}, {x2}, {y2}]")

        # Draw the bounding box
        cv2.rectangle(img, (x1, y1), (x2, y2), (0, 255, 0), 2) # Green box, thickness 2

        # Prepare the label text
        label = f"{class_name}: {confidence:.2f}"

        # Calculate text size for background rectangle
        (text_width, text_height), baseline = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)

        # Draw a filled rectangle behind the text for better readability
        cv2.rectangle(img, (x1, y1 - text_height - baseline), (x1 + text_width, y1), (0, 255, 0), -1) # Filled green rectangle

        # Put the label text above the box
        cv2.putText(img, label, (x1, y1 - baseline), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1) # Black text, thickness 1
    else:
         print(f"  Skipped: {class_name} (Confidence: {confidence:.2f}) - Below threshold {CONFIDENCE_THRESHOLD}")


# 5. Save the output image
print(f"Saving predicted image to {output_image_path}...")
cv2.imwrite(output_image_path, img)
print("Image saved successfully.")

# Optional: Display the image (uncomment if you have a GUI environment)
print("Displaying image (Press any key to close)...")
cv2.imshow("Detected Objects", img)
cv2.waitKey(0)
cv2.destroyAllWindows()