package com.example.lock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.FaceDetector;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    ImageAnalysis imageAnalysis;
    Preview preview;
    CoverView coverView;
    int passNum = 0;
    private static final int REQUEST_CODE = 1;
    Bitmap bitmap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTheme(android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        View rootView = this.getWindow().getDecorView();
        addCover(rootView);

        ImageButton add = findViewById(R.id.addButton);
        add.setOnClickListener(l -> {
          if(!coverView.shoot())
          {
              Toast.makeText(this, "摄取失败，请在识别成功后进行摄取", Toast.LENGTH_SHORT).show();
          }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            initPreview();
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE);
        }

    }


    void initPreview() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));
    }


    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
        PreviewView previewView = findViewById(R.id.previewView);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        imageAnalysis =
                new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
        ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    //check format
                    int format = imageProxy.getFormat();
                    if (format != ImageFormat.YUV_420_888) {
                        Log.i("format", "" + format);
                    }
                    @SuppressLint("UnsafeOptInUsageError") Image image = imageProxy.getImage();
                    assert image != null;
                    bitmap = createBitmap(image);
                    //check face
                    FaceDetector face_detector = new FaceDetector(bitmap.getWidth(), bitmap.getHeight(), 1);
                    // Log.i("size", "width:" + bitmap.getWidth() + " height:" + bitmap.getHeight());
                    FaceDetector.Face[] faces = new FaceDetector.Face[1];
                    int face_count = face_detector.findFaces(bitmap, faces);
                    if (face_count > 0) {
                        passNum++;
                        coverView.showCover(faces, bitmap);
                        if (passNum > 10) {
                            coverView.faceAccepted();
                        }
                        if (passNum > 20) {
                            coverView.irisAccepted();
                        }
                    } else {
                        passNum = 0;
                        coverView.focusLost();
                    }
                    coverView.invalidate();
                    imageProxy.close();
                }
        );
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);

    }


    void addCover(View view) {
        coverView = new CoverView(this);
        ((FrameLayout) view).addView(coverView);
    }

    private Bitmap createBitmap(Image image) {
        assert image != null;
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap metaBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        metaBitmap = rotateBitmap(metaBitmap);
        metaBitmap = flipBitmap(metaBitmap);
        return metaBitmap.copy(Bitmap.Config.RGB_565, true);
    }

    private Bitmap rotateBitmap(Bitmap bitmap) {
        Matrix matrix_rotate = new Matrix();
        matrix_rotate.postRotate(-90);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix_rotate, true);
    }

    private Bitmap flipBitmap(Bitmap bitmap) {
        Matrix matrix_flip = new Matrix();
        matrix_flip.postScale(-1, 1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix_flip, true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initPreview();
            } else {
                finish();
            }
        }
    }


}