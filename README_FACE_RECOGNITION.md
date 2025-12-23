# Face Recognition Setup

To enable accurate face recognition, you need to add the TensorFlow Lite model file to your project.

1.  **Download the Model:**
    Download the `mobile_face_net.tflite` model. You can find it in various open-source repositories or use a standard MobileFaceNet model converted to TFLite.
    
    Example source: [GitHub - sirius-ai/MobileFaceNet_TF](https://github.com/sirius-ai/MobileFaceNet_TF) (You might need to convert it or find a pre-converted `.tflite` file).
    
    A common pre-converted model can be found here:
    [mobile_face_net.tflite](https://github.com/shubham0204/Face-Recognition-TFLite-Android/blob/master/app/src/main/assets/mobile_face_net.tflite)

2.  **Place the File:**
    Create an `assets` folder in `app/src/main/` if it doesn't exist.
    Copy the `mobile_face_net.tflite` file into `app/src/main/assets/`.

    Path: `app/src/main/assets/mobile_face_net.tflite`

3.  **Build and Run:**
    Rebuild your app. The face recognition feature will now initialize correctly.

**Note:** The app will show an error message on the Face Detection screen if this file is missing.

