// This line declares the package where this file belongs.
package com.example.camera2example;

// --- Import Statements ---
// These lines import necessary classes from the Android SDK.
import androidx.core.app.ActivityCompat;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

// This is the declaration of our CameraHelper class. It will contain all the logic for managing the camera.
public class CameraHelper {

    // --- Class Variables (Member Variables) ---
    private final Activity activity;
    private final TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;

    // --- ZOOM-RELATED VARIABLES ---
    // ADD THIS VARIABLE: A Rect that will store the full dimensions of the camera sensor. This is our base for calculating zoom.
    private Rect sensorRect;
    // ADD THIS VARIABLE: This will store the maximum digital zoom level the hardware reports it can support. Default is 1.0 (no zoom).
    private float maxZoom = 1.0f;

    // --- Constructor ---
    // The constructor is called when a new CameraHelper object is created (in MainActivity).
    public CameraHelper(Activity activity, TextureView textureView) {
        this.activity = activity;
        this.textureView = textureView;
    }

    // --- Core Methods ---

    // This method sets up and starts the camera.
    public void startCamera() {
        CameraManager manager = (CameraManager) activity.getSystemService(Activity.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];

            // --- GET CAMERA CHARACTERISTICS FOR ZOOM ---
            // ADD THIS LINE: We get the camera's static properties, like sensor size and zoom capabilities.
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            // ADD THIS LINE: From the characteristics, we get the 'active array size', which is the full pixel area of the sensor. We store it in 'sensorRect'.
            sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            // ADD THIS LINE: We ask for the maximum digital zoom ratio the camera hardware supports. It can be null if not supported.
            Float maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            // ADD THIS 'if' BLOCK: We check if the device reported a max zoom value. If it did, we update our 'maxZoom' variable.
            if (maxDigitalZoom != null) {
                maxZoom = maxDigitalZoom;
            }

            imageDimension = new Size(640, 480);
            imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.JPEG, 3);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    saveImage(image);
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }, null);

            if (ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
            manager.openCamera(cameraId, stateCallback, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private void createCameraPreview() {
        try {
            Surface surface = new Surface(textureView.getSurfaceTexture());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    // MODIFIED LINE: Instead of calling setRepeatingRequest directly, we call our new helper method.
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Toast.makeText(activity, "Configuration failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // ADD THIS ENTIRE METHOD: This public method will be called from MainActivity whenever the SeekBar's value changes.
    public float setZoom(int progress) {
        // ADD THIS 'if' BLOCK: A safety check. If zoom isn't supported (maxZoom is 1.0) or we don't know the sensor size yet, do nothing.
        if (sensorRect == null || maxZoom <= 1.0f) {
            return 1.0f;
        }

        // ADD THIS LINE: Calculate the desired zoom level. A progress of 0 is 1.0x (no zoom). A progress of 100 is 'maxZoom'.
        float zoomLevel = 1.0f + (progress / 500.0f) * (maxZoom - 1.0f);

        // ADD THIS LINE: Calculate the width of the new crop region by dividing the full sensor width by the zoom level.
        int cropWidth = (int) (sensorRect.width() / zoomLevel);
        // ADD THIS LINE: Calculate the height of the new crop region similarly.
        int cropHeight = (int) (sensorRect.height() / zoomLevel);

        // ADD THIS LINE: Calculate the 'left' coordinate for the crop rectangle to keep the zoom centered.
        int left = (sensorRect.width() - cropWidth) / 2;
        // ADD THIS LINE: Calculate the 'top' coordinate for the crop rectangle.
        int top = (sensorRect.height() - cropHeight) / 2;

        // ADD THIS LINE: Create the new 'Rect' object that represents the cropped (zoomed) area.
        Rect zoomRect = new Rect(left, top, left + cropWidth, top + cropHeight);

        // ADD THIS LINE: Set the desired crop region on our main capture request builder. This is the key step that tells the camera to zoom.
        captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);

        // ADD THIS LINE: Call updatePreview() to apply the new zoom setting to the live camera feed.
        updatePreview();
        return zoomLevel;

    }

    // ADD THIS ENTIRE METHOD: A new helper method to apply settings and refresh the camera preview.
    private void updatePreview() {
        // ADD THIS 'if' BLOCK: A safety check to make sure the camera and session are ready.
        if (cameraDevice == null || captureSession == null) {
            return;
        }
        try {
            // ADD THIS LINE: Tell the capture session to start sending a continuous stream of frames with our latest settings (including zoom).
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void takePicture() {
        if (cameraDevice == null) return;
        try {
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            // --- APPLY ZOOM TO CAPTURE ---
            // ADD THIS LINE: To ensure the photo is taken with the same zoom as the preview, we copy the crop region from the preview builder to the still capture builder.
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, captureRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION));

            captureBuilder.addTarget(imageReader.getSurface());
            captureSession.capture(captureBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void saveImage(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        OutputStream outputStream = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "Camera2_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Camera2Example");

            Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                outputStream = activity.getContentResolver().openOutputStream(uri);
                outputStream.write(bytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}
