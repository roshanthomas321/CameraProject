// This line declares the package where this file belongs.
// Packages are used in Java to group related classes. Think of it like a folder.
package com.example.camera2example;

// These are 'import' statements. They bring in pre-written code from the Android SDK
// so we can use classes like 'Bundle', 'Button', 'View', etc., without having to write them from scratch.
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
// ADD THIS IMPORT: This line imports the SeekBar class, which is the UI slider we'll use for zoom.
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.annotation.NonNull; // An annotation that indicates a parameter or return value can't be null.
import androidx.appcompat.app.AppCompatActivity; // The base class for activities that use the modern app bar.
import androidx.core.app.ActivityCompat; // Helper for accessing features in 'Activity'.

// This declares the main class of our screen (Activity).
// 'public' means it can be accessed by other classes.
// 'extends AppCompatActivity' means our class inherits all the standard behavior of an Android screen.
public class MainActivity extends AppCompatActivity {

    // --- Class Variables (Member Variables) ---
    // These variables are declared here to be accessible throughout the entire class.

    // A TextureView is a special UI element that can display a continuous stream of content,
    // like a video or, in our case, the live feed from the camera.
    private TextureView textureView;

    // A standard button UI element that the user can press.
    private Button btnCapture;

    // ADD THIS VARIABLE: This variable will hold a reference to the SeekBar UI element from our layout.
    private SeekBar zoomSeekBar;

    // A custom class we've created to handle all the complex camera logic.
    // This helps keep our MainActivity clean and organized.
    private CameraHelper cameraHelper;

    // This is a constant integer used as a request code. When we ask for permissions,
    // we pass this code. When the user responds, Android gives us this code back so we know
    // which permission request the response is for. The number 200 is arbitrary.
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    // --- Android Lifecycle Methods ---

    // The onCreate() method is the first method called when the activity is created.
    // It's where you do all the initial setup. 'savedInstanceState' is used to restore
    // the activity to a previous state if it was destroyed and is now being recreated.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 'super.onCreate()' calls the onCreate method of the parent class (AppCompatActivity).
        // This is important because the parent class does its own setup. You must always call it first.
        super.onCreate(savedInstanceState);

        // This line connects our Java code to the XML layout file (activity_main.xml).
        // R.layout.activity_main is an ID that points to that file, which defines the user interface.
        setContentView(R.layout.activity_main);

        // Here, we find the UI elements from our XML layout by their ID and assign them to our variables.
        // 'findViewById' searches the layout for a view with the given ID.
        textureView = findViewById(R.id.textureView); // This gets the TextureView for the camera preview.
        btnCapture = findViewById(R.id.btnCapture);   // This gets the Button for taking a picture.
        // ADD THIS LINE: We find the SeekBar from our layout XML file using its ID, 'zoom_seekbar'.
        zoomSeekBar = findViewById(R.id.zoom_seekbar);

        // We create a new instance of our CameraHelper class.
        // We pass 'this' (which refers to this MainActivity) and the 'textureView'
        // so the helper knows which screen it's on and where to display the camera preview.
        cameraHelper = new CameraHelper(this, textureView);

        // This sets up a "listener" for our capture button. The code inside onClick()
        // will run every time the user clicks the button.
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // When the button is clicked, we call the takePicture() method in our CameraHelper class.
                // This delegates the action of taking a photo to our helper.
                cameraHelper.takePicture();
            }
        });

        // ADD THIS ENTIRE BLOCK: This sets up a listener for the SeekBar to detect when the user moves the slider.
        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            // This method is called continuously as the user drags the slider's thumb.
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 'fromUser' is true if the change was initiated by the user (not programmatically).
                if (fromUser) {
                    // We call the 'setZoom' method in our CameraHelper, passing the current progress (0-100).
                    cameraHelper.setZoom(progress);
                }
            }

            // This method is called when the user first touches the slider.
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // We don't need to do anything here, but the method must be implemented.
            }

            // This method is called when the user lifts their finger off the slider.
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // We don't need to do anything here either.
            }
        });


        // --- Permission Handling ---
        // We must ask the user for permission to use the camera and save files.

        // This 'if' statement checks if the app has been granted CAMERA permission OR WRITE_EXTERNAL_STORAGE permission.
        // '!=' means "not equal to". PackageManager.PERMISSION_GRANTED is the status for an approved permission.
        // So, if either permission is NOT granted...
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // ...then we request the permissions from the user.
            // This pops up a dialog box asking the user to "Allow" or "Deny".
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, // The list of permissions we need.
                    REQUEST_CAMERA_PERMISSION); // The request code we defined earlier.
        } else {
            // If we already have the permissions, we can go ahead and start the camera.
            cameraHelper.startCamera();
        }
    }

    // This method is automatically called by Android after the user responds to a permission request.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // We first check if the result code matches our camera permission request code.
        // This is important if your app requests multiple permissions at different times.
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            // 'grantResults' is an array containing the results for each permission requested.
            // We check if the array is not empty and if the first result is PERMISSION_GRANTED.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // If permission was granted by the user, we can now start the camera.
                cameraHelper.startCamera();
            } else {
                // If permission was denied, we show a 'Toast' message (a small pop-up)
                // to inform the user that the feature won't work without the permission.
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
        // It's good practice to call the superclass method to handle any other cases.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // onPause() is called when the activity is no longer in the foreground (e.g., user presses home button).
    // It's crucial to release system resources like the camera here.
    @Override
    protected void onPause() {
        // We tell our helper to close the camera, which releases it for other apps to use.
        cameraHelper.closeCamera();
        // Always call the superclass method.
        super.onPause();
    }

    // onResume() is called when the activity comes back into the foreground.
    @Override
    protected void onResume() {
        // Always call the superclass method first.
        super.onResume();
        // We check for permission again because the user could have revoked it in the app settings.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // If we have permission, we start the camera. The cameraHelper is smart enough
            // to handle setting up the preview on our textureView.
            cameraHelper.startCamera();
        }
    }
}
