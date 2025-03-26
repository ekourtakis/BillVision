from tflite_support.metadata_writers import image_classifier
from tflite_support.metadata_writers import writer_utils
from tflite_support import metadata

MODEL_PATH = "../android-app/app/src/main/ml/usd_detector.tflite"
LABEL_PATH = "./labels.txt"
SAVE_TO_PATH = MODEL_PATH

INPUT_NORM_MEAN = 0.0
INPUT_NORM_STD = 1.0

ImageClassifierWriter = image_classifier.MetadataWriter

writer = ImageClassifierWriter.create_for_inference(
    writer_utils.load_file(MODEL_PATH), 
    [INPUT_NORM_MEAN],
    [INPUT_NORM_STD],
    [LABEL_PATH]
)

print(writer.get_metadata_json())

writer_utils.save_file(writer.populate(), SAVE_TO_PATH)

def display_labels_from_tflite(model_path):
    displayer = metadata.MetadataDisplayer.with_model_file(model_path)

    associated_files = displayer.get_packed_associated_file_list()

    for file in associated_files:
        if file == "labels.txt":
            labels = displayer.get_associated_file_buffer(file)
            print("Class labels from the TFLite model:")
            print(labels.decode('utf-8'))
            return

    print("No labels found in the model metadata.")

display_labels_from_tflite(SAVE_TO_PATH)
