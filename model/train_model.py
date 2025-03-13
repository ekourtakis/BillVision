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
    project = rf.workspace("jm-cbkgb").project("usd_total")
    version = project.version(1)
    dataset = version.download("tfrecord")  # Changed to tfrecord format
    
    # Function to parse TFRecord
    def parse_tfrecord(example_proto):
        feature_description = {
            'image/encoded': tf.io.FixedLenFeature([], tf.string),
            'image/object/class/label': tf.io.VarLenFeature(tf.int64),
            'image/object/bbox/xmin': tf.io.VarLenFeature(tf.float32),
            'image/object/bbox/ymin': tf.io.VarLenFeature(tf.float32),
            'image/object/bbox/xmax': tf.io.VarLenFeature(tf.float32),
            'image/object/bbox/ymax': tf.io.VarLenFeature(tf.float32),
        }
        
        example = tf.io.parse_single_example(example_proto, feature_description)
        
        # Decode the image
        image = tf.io.decode_jpeg(example['image/encoded'], channels=3)  # Decode JPEG image
        image = tf.image.resize(image, [224, 224])  # Resize to 224x224
        image = tf.cast(image, tf.float32) / 255.0  # Normalize to [0, 1]
        
        # Get the first label and subtract 1 to normalize to [0, num_classes - 1]
        label = tf.sparse.to_dense(example['image/object/class/label'])[0] - 1
        
        return image, label

    # Load training dataset
    train_dataset = tf.data.TFRecordDataset('USD_Total-1/train/money.tfrecord')
    train_dataset = train_dataset.map(parse_tfrecord)
    train_dataset = train_dataset.shuffle(1000).batch(32).repeat()  # Add .repeat()

    # Load validation dataset
    val_dataset = tf.data.TFRecordDataset('USD_Total-1/valid/money.tfrecord')
    val_dataset = val_dataset.map(parse_tfrecord)
    val_dataset = val_dataset.batch(32)

    # Data augmentation (used only during training)
    data_augmentation = keras.Sequential([
        keras.layers.RandomFlip("horizontal"),
        keras.layers.RandomRotation(0.1),
        keras.layers.RandomZoom(0.1),
    ])

    # Build the model
    model = keras.Sequential([
        keras.layers.InputLayer(shape=(224, 224, 3)),
        data_augmentation,  # Data augmentation is part of the model during training
        keras.layers.Conv2D(32, 3, activation='relu'),
        keras.layers.MaxPooling2D(),
        keras.layers.Conv2D(64, 3, activation='relu'),
        keras.layers.MaxPooling2D(),
        keras.layers.Conv2D(64, 3, activation='relu'),
        keras.layers.MaxPooling2D(),
        keras.layers.Dropout(0.2),
        keras.layers.Flatten(),
        keras.layers.Dense(128, activation='relu'),
        keras.layers.Dense(7, activation='softmax')  # 7 classes for different denominations
    ])

    model.compile(
        optimizer='adam',
        loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=False),
        metrics=['accuracy']
    )
    
    # Calculate steps_per_epoch and validation_steps
    num_train_samples = 2106  # Number of training images
    num_val_samples = 593     # Number of validation images
    batch_size = 32
    steps_per_epoch = num_train_samples // batch_size
    validation_steps = num_val_samples // batch_size

    # Train the model for 1 epoch (for testing)
    history = model.fit(
        train_dataset,
        validation_data=val_dataset,
        epochs=10,
        steps_per_epoch=steps_per_epoch,
        validation_steps=validation_steps
    )

    model.summary()

    # Export to TFLite
    # Create a new model without data augmentation for inference
    inference_model = keras.Sequential([
        keras.layers.InputLayer(shape=(224, 224, 3)),
        keras.layers.Conv2D(32, 3, activation='relu'),
        keras.layers.MaxPooling2D(),
        keras.layers.Conv2D(64, 3, activation='relu'),
        keras.layers.MaxPooling2D(),
        keras.layers.Conv2D(64, 3, activation='relu'),
        keras.layers.MaxPooling2D(),
        keras.layers.Dropout(0.2),
        keras.layers.Flatten(),
        keras.layers.Dense(128, activation='relu'),
        keras.layers.Dense(7, activation='softmax')  # 7 classes for different denominations
    ])

    # Copy the trained weights from the original model to the inference model
    for layer, inference_layer in zip(model.layers[1:], inference_model.layers):
        inference_layer.set_weights(layer.get_weights())

    # Create a representative dataset for quantization
    def representative_data_gen():
        for image, _ in train_dataset.take(100):  # Use 100 samples for calibration
            yield [image]

    # Create a concrete function for the inference model
    run_model = tf.function(lambda x: inference_model(x))
    concrete_func = run_model.get_concrete_function(
        tf.TensorSpec(inference_model.inputs[0].shape, inference_model.inputs[0].dtype)
    )

    # Convert the model to TFLite
    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func])
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_data_gen  # Add representative dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8  # Optional: Set input type to uint8
    converter.inference_output_type = tf.uint8  # Optional: Set output type to uint8

    tflite_quantized_model = converter.convert()

    # Save the TFLite model
    with open('usd_detector.tflite', 'wb') as f:
        f.write(tflite_quantized_model)

if __name__ == '__main__':
    train_model()