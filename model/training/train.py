from sklearn.model_selection import train_test_split
from tensorflow.keras.callbacks import EarlyStopping
import numpy as np
from tensorflow.keras.applications import MobileNet
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D
from tensorflow.keras.models import Model
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

# Load the data
label_map = parse_label_map('dataset/test/money_label_map.pbtxt')

# Prepare the dataset
names, ids = zip(*label_map)

# Print original labels for debugging
print("Original labels:", ids)

# Use the original labels directly (1-7)
y = np.array(ids)  # Use the original IDs directly

# Check unique labels
print("Unique labels:", np.unique(y))

# Ensure labels are in the range [1, 7]
if np.any(y < 1) or np.any(y > 7):
    raise ValueError("Labels are out of the expected range [1, 7].")

# Load and preprocess the training dataset
train_dataset = load_tfrecord_dataset('dataset/train/money.tfrecord')
X_train, y_train = preprocess_dataset(train_dataset)

# Load and preprocess the validation dataset
val_dataset = load_tfrecord_dataset('dataset/valid/money.tfrecord')
X_val, y_val = preprocess_dataset(val_dataset)

# Adjust labels to be zero-indexed
y_train = np.array(y_train) - 1  # Adjust training labels
y_val = np.array(y_val) - 1  # Adjust validation labels

# Check for valid label values
if np.any(y_train < 0) or np.any(y_train >= 7):
    raise ValueError("Training labels contain invalid values. Expected range: [0, 6].")

if np.any(y_val < 0) or np.any(y_val >= 7):
    raise ValueError("Validation labels contain invalid values. Expected range: [0, 6].")

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

# Define early stopping
early_stopping = EarlyStopping(monitor='val_loss', patience=3, restore_best_weights=True)

# Train the model for just 1 epoch to test if everything works
model.fit(X_train, y_train, epochs=1, validation_data=(X_val, y_val), callbacks=[early_stopping])

# Load and preprocess the test dataset
X_test_dataset = load_tfrecord_dataset('dataset/test/money.tfrecord')
X_test, y_test = preprocess_dataset(X_test_dataset)

# Evaluate the model
test_loss, test_accuracy = model.evaluate(X_val, y_val)
print(f"Test Loss: {test_loss}, Test Accuracy: {test_accuracy}")