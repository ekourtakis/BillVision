import tensorflow as tf
from tensorflow import keras
import numpy as np
import matplotlib.pyplot as plt
from roboflow import Roboflow
from dotenv import load_dotenv
import os

def train_model():
    # Load api key from .env file
    roboflow_api_key = os.getenv("ROBOFLOW_API_KEY")
    if not roboflow_api_key:
        raise ValueError("Roboflow API key not found in .env file.")

    # Download dataset from Roboflow
    rf = Roboflow(api_key=roboflow_api_key)
    project = rf.workspace("matias-406ns").project("usd_detection")
    version = project.version(1)
    dataset = version.download("folder")

    # Load training dataset
    train_dataset = tf.keras.preprocessing.image_dataset_from_directory(
        'usd_detection-1/train',  # Updated path to load images directly
        image_size=(224, 224),    # Resize images to 224x224 (MobileNetV2 input size)
        batch_size=32              # Set batch size
    )

    # Load validation dataset
    val_dataset = tf.keras.preprocessing.image_dataset_from_directory(
        'usd_detection-1/valid',  # Updated path to load images directly
        image_size=(224, 224),     # Resize images to 224x224
        batch_size=32               # Set batch size
    )

    train_dataset = train_dataset.repeat()
    val_dataset = val_dataset.repeat()

    # Data augmentation (used only during training)
    data_augmentation = keras.Sequential([
        keras.layers.RandomFlip("horizontal"),
        keras.layers.RandomRotation(0.1),
        keras.layers.RandomZoom(0.1),
    ])

    # Load MobileNetV2 as the base model
    base_model = tf.keras.applications.MobileNetV2(
        input_shape=(224, 224, 3),  # Input shape for MobileNetV2
        include_top=False,          # Exclude the top classification layer
        weights='imagenet'          # Use pre-trained weights from ImageNet
    )

    # Freeze the base model (so its weights are not updated during training)
    base_model.trainable = False

    # Build the model using MobileNetV2 as the base
    model = keras.Sequential([
        keras.layers.Input(shape=(224, 224, 3)),
        data_augmentation,  # Apply data augmentation
        base_model,         # Add MobileNetV2 as the base
        keras.layers.GlobalAveragePooling2D(),  # Add a global average pooling layer
        keras.layers.Dense(128, activation='relu'),  # Add a dense layer
        keras.layers.Dropout(0.2),  # Add dropout for regularization
        keras.layers.Dense(7, activation='softmax')  # Output layer for 7 classes
    ])

    # Compile the model
    model.compile(
        optimizer='adam',
        loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=False),
        metrics=['accuracy']
    )

    # Calculate steps_per_epoch and validation_steps
    num_train_samples = 11253  # Number of training images
    num_val_samples = 1072     # Number of validation images
    batch_size = 16
    steps_per_epoch = num_train_samples // batch_size
    validation_steps = num_val_samples // batch_size

    # Train the model for 10 epochs (for testing)
    history = model.fit(
        train_dataset,
        validation_data=val_dataset,
        epochs=10,  # Train for more epochs
        steps_per_epoch=steps_per_epoch,
        validation_steps=validation_steps
    )

    # Save the trained model
    model.save('mobilenetv2_usd_detector.h5')

    # Export to TFLite
    # Create a new model without data augmentation for inference
    inference_model = keras.Sequential([
        keras.layers.Input(shape=(224, 224, 3)),
        base_model,  # Use the same base model
        keras.layers.GlobalAveragePooling2D(),
        keras.layers.Dense(128, activation='relu'),
        keras.layers.Dense(7, activation='softmax')
    ])

    # Set the weights of the inference model to match the trained model
    inference_model.set_weights(model.get_weights())

    # Convert the model to TFLite
    converter = tf.lite.TFLiteConverter.from_keras_model(inference_model)
    tflite_model = converter.convert()

    # Save the TFLite model to android app
    with open('../android-app/app/src/main/ml/usd_detector.tflite', 'wb') as f:
        f.write(tflite_model)

if __name__ == '__main__':
    train_model()