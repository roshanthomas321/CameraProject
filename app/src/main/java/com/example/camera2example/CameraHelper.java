package com.example.camera2example;

import androidx.core.app.ActivityCompat;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager; // <-- ADD THIS LINE
import android.graphics.ImageFormat;
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
public class CameraHelper {

    private final Activity activity;
    private final TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;

    public CameraHelper(Activity activity, TextureView textureView) {
        this.activity = activity;
        this.textureView = textureView;
    }

    public void startCamera() {
        CameraManager manager = (CameraManager) activity.getSystemService(Activity.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0]; // rear camera
            imageDimension = new Size(640, 480);
            imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(),
                    ImageFormat.JPEG, 3);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                saveImage(image);
                image.close();
            }, null);

            if (ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
            manager.openCamera(cameraId, stateCallback, null);

        } catch (Exception e) { e.printStackTrace(); }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice camera) { cameraDevice = camera; createCameraPreview(); }
        @Override public void onDisconnected(CameraDevice camera) { cameraDevice.close(); }
        @Override public void onError(CameraDevice camera, int error) { cameraDevice.close(); cameraDevice = null; }
    };

    private void createCameraPreview() {
        try {
            Surface surface = new Surface(textureView.getSurfaceTexture());
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    try { captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null); } catch (CameraAccessException e) { e.printStackTrace(); }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) { Toast.makeText(activity, "Configuration failed", Toast.LENGTH_SHORT).show(); }
            }, null);
        } catch (CameraAccessException e) { e.printStackTrace(); }
    }

    public void takePicture() {
        if (cameraDevice == null) return;
        try {
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureSession.capture(captureBuilder.build(), null, null);
        } catch (CameraAccessException e) { e.printStackTrace(); }
    }

    private void saveImage(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "Camera2_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Camera2Example");
            Uri uri = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                OutputStream outputStream = activity.getContentResolver().openOutputStream(uri);
                outputStream.write(bytes);
                outputStream.close();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void closeCamera() {
        if (captureSession != null) captureSession.close();
        if (cameraDevice != null) cameraDevice.close();
        if (imageReader != null) imageReader.close();
    }
}