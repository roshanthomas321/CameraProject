package com.example.camera2example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // --- UI and Camera Variables ---
    private TextureView textureView;
    private Button btnCapture;
    private SeekBar zoomSeekBar;
    private TextView zoomLevelTextView;
    private CameraHelper cameraHelper;

    private static final int REQUEST_CAMERA_PERMISSION = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find all UI elements from the layout
        textureView = findViewById(R.id.textureView);
        btnCapture = findViewById(R.id.btnCapture);
        zoomSeekBar = findViewById(R.id.zoom_seekbar);
        zoomLevelTextView = findViewById(R.id.zoom_level_text);

        // Initialize the camera helper
        cameraHelper = new CameraHelper(this, textureView);

        // Set the initial zoom level text
        zoomLevelTextView.setText("1.0x");

        // Set listener for the capture button
        btnCapture.setOnClickListener(v -> cameraHelper.takePicture());

        // Set listener for the zoom SeekBar
        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float currentZoom = cameraHelper.setZoom(progress);
                    // Update the text view as the slider moves
                    zoomLevelTextView.setText(String.format(Locale.US, "%.1fx", currentZoom));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No action needed
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // No action needed
            }
        });

        // --- Permission Handling ---
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            cameraHelper.startCamera();
        }
    }

    /**
     * Overridden method to intercept hardware key presses.
     * This is where we handle the touchpad gestures from the Vuzix device.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d("MainActivity", "Key pressed: " + keyCode); // Log for debugging

        switch (keyCode) {
            // Two-finger swipe bottom to top (as per docs) -> Zoom Out
            case KeyEvent.KEYCODE_VOLUME_UP:
                Log.d("MainActivity", "Zoom Out gesture detected.");
                float zoomOutLevel = cameraHelper.zoomOut();
                updateZoomUI(zoomOutLevel); // Update on-screen controls
                return true; // Event handled

            // Two-finger swipe top to bottom (as per docs) -> Zoom In
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                Log.d("MainActivity", "Zoom In gesture detected.");
                float zoomInLevel = cameraHelper.zoomIn();
                updateZoomUI(zoomInLevel); // Update on-screen controls
                return true; // Event handled

            // One-finger tap (as per docs) -> Take Picture
            case KeyEvent.KEYCODE_DPAD_CENTER:
                Log.d("MainActivity", "Capture gesture detected.");
                cameraHelper.takePicture();
                btnCapture.performClick(); // Provide visual feedback
                return true; // Event handled
        }

        // For any other key, let the system handle it
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Helper method to synchronize the SeekBar and TextView with the current zoom level.
     * @param zoomLevel The current magnification level (e.g., 1.5f).
     */
    private void updateZoomUI(float zoomLevel) {
        // Update the TextView (e.g., "1.5x")
        zoomLevelTextView.setText(String.format(Locale.US, "%.1fx", zoomLevel));

        // Update the SeekBar's position to match the zoom level from gestures
        if (cameraHelper.getMaxZoom() > 1) {
            int progress = (int) (((zoomLevel - 1.0f) / (cameraHelper.getMaxZoom() - 1.0f)) * 100);
            zoomSeekBar.setProgress(progress);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraHelper.startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to use this app", Toast.LENGTH_LONG).show();
                finish(); // Close the app if permission is denied
            }
        }
    }

    @Override
    protected void onPause() {
        cameraHelper.closeCamera();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraHelper.startCamera();
        }
    }
}
