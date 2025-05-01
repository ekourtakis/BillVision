# BillVision

BillVision is an Android app that detects and classifies US dollar bills in real time for visually impaired users.

## Android App
To run the Android app:
1. Open the `android-app` directory in Android Studio.
2. Build and run the app on an emulator or a physical device.

## Model Training
To generate a new version of the model:
1. Ensure Python 3.12 is installed and your computure has a compatible NVIDIA GPU.
2. In the model directory, run this command:
   ```sh
   cp .env-example .env
   ```
3. Follow [these instructions](https://docs.roboflow.com/api-reference/authentication) to retrieve an API key from Roboflow. Paste the key as the value of ROBOFLOW_API_KEY in the new `.env` file.
4. Create and activate a virtual environment:
   ```sh
   python3.12 -m venv .venv
   source .venv/bin/activate
   ```
5. Install the dependencies:
    ```sh
    pip install --requirement requirements.txt
    ```
6. Run the training script:
   ```sh
   python train_model.py
   ```
   The script trains a new model and, if successful, exports it as a TFLite file. The exported model is saved to the Android app's assets directory, making it ready for immediate testing. Run `deactivate` to exit the virtual environment.