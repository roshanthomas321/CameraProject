package com.example.camera2example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private TextureView textureView;
    private Button btnCapture;
    private CameraHelper cameraHelper;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView); // Live camera view
        btnCapture = findViewById(R.id.btnCapture);   // Button to capture photo

        cameraHelper = new CameraHelper(this, textureView); // Handles camera logic

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { cameraHelper.takePicture(); }
        });

        // Request Camera & Storage permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            cameraHelper.startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraHelper.startCamera();
            } else { Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show(); }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override protected void onPause() { cameraHelper.closeCamera(); super.onPause(); }
    @Override protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraHelper.startCamera();
        }
    }
}