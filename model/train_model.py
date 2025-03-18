import tensorflow as tf
from tensorflow import keras
from tensorflow.keras.callbacks import EarlyStopping
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

    train_dataset = train_dataset.map(lambda x, y: (x / 255.0, y))
    val_dataset = val_dataset.map(lambda x, y: (x / 255.0, y))

    # train_dataset = train_dataset.shuffle(buffer_size=1000)
    # val_dataset = val_dataset.shuffle(buffer_size=1000)

    # AUTOTUNE = tf.data.AUTOTUNE
    # train_dataset = train_dataset.prefetch(buffer_size=AUTOTUNE)
    # val_dataset = val_dataset.prefetch(buffer_size=AUTOTUNE)


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
    num_train_samples = 17040  # Number of training images
    num_val_samples = 534     # Number of validation images
    batch_size = 16
    steps_per_epoch = num_train_samples // batch_size
    validation_steps = num_val_samples // batch_size

    # Early stopping to prevent overfitting
    early_stopping = EarlyStopping(
        monitor='val_loss',      # Monitor validation loss
        patience=3,              # Stop after 3 epochs of no improvement
        restore_best_weights=True # Restore the best weights at the end
    )

    history = model.fit(
        train_dataset,
        validation_data=val_dataset,
        epochs=25,  # Train for more epochs
        steps_per_epoch=steps_per_epoch,
        validation_steps=validation_steps,
        callbacks=[early_stopping] 
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