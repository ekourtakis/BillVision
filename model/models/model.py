import tensorflow as tf

def create_model(num_classes):
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(224, 224, 3)),  # Input layer for images
        tf.keras.layers.Conv2D(32, (3, 3), activation='relu'),
        tf.keras.layers.MaxPooling2D(pool_size=(2, 2)),
        tf.keras.layers.Flatten(),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ])
    return model 