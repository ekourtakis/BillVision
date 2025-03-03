from tensorflow.keras.callbacks import EarlyStopping
import numpy as np
from tensorflow.keras.applications import MobileNet
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D
from tensorflow.keras.models import Model
import keras
from preprocessing.data_loader import load_tfrecord_dataset
from preprocessing.data_preprocessing import preprocess_dataset

# Function to parse the label map
def parse_label_map(file_path):
    items = []
    with open(file_path, 'r') as file:
        lines = file.readlines()
        for i in range(0, len(lines), 5):
            name = lines[i + 1].split(': ')[1].strip().strip('"')
            id_str = lines[i + 2].split(': ')[1].strip().strip(',\n')
            id_ = int(id_str)
            items.append((name, id_))
    return items

def train_model(model_save_path=None):
    # Load the data
    label_map = parse_label_map('dataset/test/money_label_map.pbtxt')

    # Prepare the dataset
    names, ids = zip(*label_map)

    # Load and preprocess the training dataset
    train_dataset = load_tfrecord_dataset('dataset/train/money.tfrecord')
    X_train, y_train = preprocess_dataset(train_dataset)

    # Load and preprocess the validation dataset
    val_dataset = load_tfrecord_dataset('dataset/valid/money.tfrecord')
    X_val, y_val = preprocess_dataset(val_dataset)

    # Adjust labels to be zero-indexed
    y_train = np.array(y_train) - 1  # Adjust training labels
    y_val = np.array(y_val) - 1  # Adjust validation labels

    # Load the pretrained MobileNet model without the top layer
    base_model = MobileNet(weights='imagenet', include_top=False, input_shape=(224, 224, 3))

    # Add custom layers on top
    x = base_model.output
    x = GlobalAveragePooling2D()(x)
    predictions = Dense(7, activation='softmax')(x)  # 7 classes (1-7)

    # Create the final model
    model = Model(inputs=base_model.input, outputs=predictions)

    # Compile the model
    model.compile(optimizer='adam', loss='sparse_categorical_crossentropy', metrics=['accuracy'])

    # Define early stopping with slightly more patience
    early_stopping = EarlyStopping(
        monitor='val_loss',
        patience=5,  # Increased from 3 to 5 to give more chances for improvement
        restore_best_weights=True,
        verbose=1  # Add verbosity to see when early stopping triggers
    )

    # Train the model with more epochs
    history = model.fit(
        X_train, 
        y_train,
        epochs=30,  # Increased from 1 to 30
        batch_size=32,
        validation_data=(X_val, y_val),
        callbacks=[early_stopping],
        verbose=1
    )

    # Load and preprocess the test dataset
    X_test_dataset = load_tfrecord_dataset('dataset/test/money.tfrecord')
    X_test, y_test = preprocess_dataset(X_test_dataset)

    # Evaluate the model
    test_loss, test_accuracy = model.evaluate(X_val, y_val)
    print(f"Test Loss: {test_loss}, Test Accuracy: {test_accuracy}")
    
    # Save the model if a path is provided
    if model_save_path:
        if not model_save_path.endswith('.keras'):
            model_save_path = model_save_path.replace('.h5', '.keras')
        # Remove the save_format argument as it's inferred from .keras extension
        keras.saving.save_model(model, model_save_path)
        print(f"Model saved to {model_save_path}")
    
    return model

if __name__ == '__main__':
    train_model('models/money_classifier.keras')
