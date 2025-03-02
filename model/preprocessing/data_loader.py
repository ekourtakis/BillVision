import tensorflow as tf

def load_tfrecord_dataset(file_path):
    raw_dataset = tf.data.TFRecordDataset(file_path)

    def _parse_function(proto):
        feature_description = {
            'image/encoded': tf.io.FixedLenFeature([], tf.string),
            'image/object/class/label': tf.io.FixedLenFeature([], tf.int64),  # Correct feature name
        }
        return tf.io.parse_single_example(proto, feature_description)

    parsed_dataset = raw_dataset.map(_parse_function)
    return parsed_dataset