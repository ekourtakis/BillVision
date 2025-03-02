import tensorflow as tf

def preprocess_dataset(dataset):
    images = []
    labels = []

    def _preprocess_function(example):
        image = tf.image.decode_jpeg(example['image/encoded'], channels=3)
        image = tf.image.resize(image, [224, 224])
        image = image / 255.0
        label = example['image/object/class/label']
        return image, label

    for example in dataset:
        image, label = _preprocess_function(example)
        images.append(image)
        labels.append(label)

    return tf.convert_to_tensor(images), tf.convert_to_tensor(labels) 