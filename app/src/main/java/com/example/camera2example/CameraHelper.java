package com.example.camera2example;

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
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class CameraHelper {

    // --- Class Variables ---
    private final Activity activity;
    private final TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;

    // --- Zoom-Related Variables ---
    private Rect sensorRect;
    private float maxZoom = 1.0f;
    private boolean hasOpticalZoom = false;
    private static final int ZOOM_STEPS = 20; // Number of discrete steps for gesture zoom
    private int currentZoomStep = 0;

    public CameraHelper(Activity activity, TextureView textureView) {
        this.activity = activity;
        this.textureView = textureView;
    }

    public void startCamera() {
        CameraManager manager = (CameraManager) activity.getSystemService(Activity.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            // --- Logic to check for Optical Zoom ---
            final float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            if (focalLengths != null && focalLengths.length > 1) {
                hasOpticalZoom = true;
                Log.d("CameraHelper", "Optical zoom supported. Focal lengths: " + Arrays.toString(focalLengths));
            } else {
                hasOpticalZoom = false;
                Log.d("CameraHelper", "Optical zoom NOT supported.");
            }

            // --- Digital Zoom Setup ---
            sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Float maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
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
                    if (image != null) image.close();
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

    /**
     * Sets the zoom level based on a progress value (0-100).
     * This is used by the SeekBar and the gesture methods.
     * @return The calculated zoom level (e.g., 1.5f).
     */
    public float setZoom(int progress) {
        if (sensorRect == null || maxZoom <= 1.0f) return 1.0f;

        // Keep internal step tracker in sync
        this.currentZoomStep = (int) ((progress / 100.0f) * ZOOM_STEPS);

        float zoomLevel = 1.0f + (progress / 100.0f) * (maxZoom - 1.0f);

        int cropWidth = (int) (sensorRect.width() / zoomLevel);
        int cropHeight = (int) (sensorRect.height() / zoomLevel);
        int left = (sensorRect.width() - cropWidth) / 2;
        int top = (sensorRect.height() - cropHeight) / 2;

        Rect zoomRect = new Rect(left, top, left + cropWidth, top + cropHeight);
        captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
        updatePreview();
        return zoomLevel;
    }

    // --- Methods for Gesture Control ---

    public float zoomIn() {
        currentZoomStep = Math.min(ZOOM_STEPS, currentZoomStep + 1);
        int progress = (int) (((float) currentZoomStep / ZOOM_STEPS) * 100);
        return setZoom(progress);
    }

    public float zoomOut() {
        currentZoomStep = Math.max(0, currentZoomStep - 1);
        int progress = (int) (((float) currentZoomStep / ZOOM_STEPS) * 100);
        return setZoom(progress);
    }

    private void updatePreview() {
        if (cameraDevice == null || captureSession == null) return;
        try {
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void takePicture() {
        if (cameraDevice == null) return;
        try {
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // Apply the same zoom to the still capture as the preview
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, captureRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION));
            captureBuilder.addTarget(imageReader.getSurface());
            captureSession.capture(captureBuilder.build(), null, null);
            Toast.makeText(activity, "Picture Taken!", Toast.LENGTH_SHORT).show();
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
                if (outputStream != null) outputStream.close();
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

    // --- Public Getters ---
    public float getMaxZoom() {
        return maxZoom;
    }

    public boolean isOpticalZoomSupported() {
        return hasOpticalZoom;
    }
}
