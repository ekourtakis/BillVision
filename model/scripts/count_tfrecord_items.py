import tensorflow as tf
import sys

def count_tfrecord_items(file_path):
    raw_dataset = tf.data.TFRecordDataset(file_path)
    count = sum(1 for _ in raw_dataset)  # Count the number of records
    return count

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python count_tfrecord_items.py <tfrecord_file_path>")
        sys.exit(1)
    file_path = sys.argv[1]
    item_count = count_tfrecord_items(file_path)
    print(f'Total number of items in the TFRecord file: {item_count}')
