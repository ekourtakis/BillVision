import tensorflow as tf

def inspect_tfrecord(file_path):
    raw_dataset = tf.data.TFRecordDataset(file_path)
    for raw_record in raw_dataset.take(5):  # Print the first 5 records
        example = tf.train.Example()
        example.ParseFromString(raw_record.numpy())
        print(example)

if __name__ == "__main__":
    inspect_tfrecord('./dataset/test/money.tfrecord')  # Update with your actual path
