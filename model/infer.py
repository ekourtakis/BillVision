import tensorflow as tf
import numpy as np
from PIL import Image
import sys  # Import sys to access command line arguments

# Load the TFLite model
interpreter = tf.lite.Interpreter(model_path='../android-app/app/src/main/ml/usd_detector.tflite')
interpreter.allocate_tensors()

# Get input and output details
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

# Define a label dictionary based on the Kotlin enum
LABEL_DICTIONARY = {
    0: "Hundred Dollar",
    1: "Ten Dollar",
    2: "One Dollar",
    3: "Twenty Dollar",
    4: "Two Dollar",
    5: "Fifty Dollar",
    6: "Five Dollar"
}

def preprocess_image(image_path):
    # Load image using PIL
    image = Image.open(image_path).convert('RGB')
    image = image.resize((224, 224))  # Resize to model input size
    image = np.expand_dims(image, axis=0)  # Add batch dimension
    image = image.astype(np.float32) / 255.0  # Convert to float32 and normalize
    return image

def run_inference(image_path):
    # Preprocess image
    input_data = preprocess_image(image_path)

    # Set the input tensor
    interpreter.set_tensor(input_details[0]['index'], input_data)

    # Run inference
    interpreter.invoke()

    # Get the output tensor
    output = interpreter.get_tensor(output_details[0]['index'])
    predicted_class = np.argmax(output)
    confidence = output[0][predicted_class]  # Get the confidence score for the predicted class

    return predicted_class, confidence, output

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python infer.py <image_path>")
        sys.exit(1)

    image_path = sys.argv[1]  # Get the image path from command line arguments
    predicted_class, confidence, output = run_inference(image_path)

    print(f"Predicted class: {LABEL_DICTIONARY[predicted_class]}")
    print(f"Confidence: {confidence}")  # Print confidence with two decimal places
    print(f"Raw output: {output}")
