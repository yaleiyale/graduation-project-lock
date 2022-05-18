package com.example.lock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.FaceDetector;
import android.media.Image;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MainActivity extends AppCompatActivity {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    ImageAnalysis imageAnalysis;
    Preview preview;
    CoverView coverView;
    int passNum = 0;
    private static final int REQUEST_CODE = 1;
    Bitmap bitmap;
    private int did;
    NetService netService;
    String filename = "did";
    TextView result_judge;
    Boolean uploadPattern = false;
    ImageButton bt_change;
    ImageButton bt_register;
    ImageButton bt_add;
    String uid = "1";
    String password = "1";
    boolean working = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTheme(android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        File file = new File(getApplicationContext().getFilesDir(), filename);
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                fis = getApplicationContext().openFileInput(filename);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8);
            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
                String line = reader.readLine();
                while (line != null) {
                    stringBuilder.append(line);
                    line = reader.readLine();
                }
            } catch (IOException e) {
                // Error occurred when opening raw file for reading.
            } finally {
                String contents = stringBuilder.toString();
                Log.i("InitSuccess", "重识id");
                did = Integer.parseInt(contents);
                try {
                    assert fis != null;
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.i("InitSuccess", "设备注册成功" + did);
                Toast.makeText(this, "已注册！", Toast.LENGTH_SHORT).show();
            }
        }


        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        View rootView = this.getWindow().getDecorView();
        addCover(rootView);

        netService = NetService.create();

        bt_register = findViewById(R.id.connectButton);
        bt_register.setOnClickListener(l -> connect());

        bt_change = findViewById(R.id.changeButton);
        bt_change.setOnClickListener(l -> changePattern());

        bt_add = findViewById(R.id.addButton);
        bt_add.setOnClickListener(l -> {
            try {
                uploadPerson();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        result_judge = findViewById(R.id.text_result_judge);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            initPreview();
        } else {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE);
        }
    }

    @SuppressLint("SdCardPath")
    private void uploadPerson() throws IOException {
        {
            if (!uploadPattern) {
                Toast.makeText(this, "未启动录入模式", Toast.LENGTH_SHORT).show();
            } else {
                if (!coverView.shoot()) {
                    Toast.makeText(this, "摄取失败，请在识别成功后进行摄取", Toast.LENGTH_SHORT).show();
                } else {
                    Bitmap clearFace = bitmap;
                    if (MyUtils.evaluateClarity(clearFace)) {
                        Toast.makeText(this, "摄取成功", Toast.LENGTH_SHORT).show();
                        UploadDialog(clearFace);
                    }

                }
            }

        }
    }

    private void UploadDialog(Bitmap faceBitmap) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final AlertDialog dialog = builder.create();
        View dialogView = View.inflate(this, R.layout.upload, null);
        dialog.setView(dialogView);
        dialog.show();

        final EditText et_personName = dialogView.findViewById(R.id.et_person_name);
        final ImageView iv_showFace = dialogView.findViewById(R.id.iv_image);
        final Button btn_adjust = dialogView.findViewById(R.id.btn_adjust);
        final Button btn_cancel = dialogView.findViewById(R.id.btn_cancel);
        iv_showFace.post(() -> iv_showFace.setImageBitmap(faceBitmap));
        btn_adjust.setOnClickListener(view -> {
            String name = et_personName.getText().toString();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "请输入个人姓名", Toast.LENGTH_SHORT).show();
                return;
            }
            upload(faceBitmap, name);

            dialog.dismiss();
        });

        btn_cancel.setOnClickListener(view -> dialog.dismiss());
    }

    private void upload(Bitmap faceBitmap, String name) {

        Call<Integer> call = netService.generatePID(name);
        call.enqueue(new Callback<Integer>() {
            @Override
            public void onResponse(@NonNull Call<Integer> call, @NonNull Response<Integer> response) {
                int pid = response.body();
                try {
                    MyUtils.saveBitmap(faceBitmap, response.body());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                @SuppressLint("SdCardPath") File file = new File("/sdcard/DCIM/Camera/" + pid + ".jpg");
                RequestBody body = RequestBody.create(MediaType.parse("multipart/form-data"), file);

                MultipartBody multipartBody = new MultipartBody.Builder()
                        .addFormDataPart("file", pid + ".jpg", body)
                        .setType(MultipartBody.FORM)
                        .build();

                Call<String> uploadCall = netService.upload(multipartBody.part(0));
                uploadCall.enqueue(new Callback<String>() {
                    @Override
                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {

                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<Integer> call, @NonNull Throwable t) {

            }
        });
    }


    private void changePattern() {
        if (!uploadPattern) {
            startUploadDialog();
        } else {
            endUpload();
        }
    }

    private void endUpload() {
        uploadPattern = false;
        bt_change.post(() -> bt_change.setImageResource(R.drawable.normal_icon));
        result_judge.post(() -> result_judge.setText(""));
        Toast.makeText(this, "录入模式关闭", Toast.LENGTH_SHORT).show();
    }

    private void connect() {
        File file = new File(getApplicationContext().getFilesDir(), filename);
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                fis = getApplicationContext().openFileInput(filename);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            InputStreamReader inputStreamReader = new InputStreamReader(fis, StandardCharsets.UTF_8);
            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
                String line = reader.readLine();
                while (line != null) {
                    stringBuilder.append(line);
                    line = reader.readLine();
                }
            } catch (IOException e) {
                // Error occurred when opening raw file for reading.
            } finally {
                String contents = stringBuilder.toString();
                Log.i("InitSuccess", "重识id");
                did = Integer.parseInt(contents);
                try {
                    assert fis != null;
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.i("InitSuccess", "设备注册成功" + did);
                Toast.makeText(this, "已注册！", Toast.LENGTH_SHORT).show();
            }
        } else {
            connectDialog(file);
        }
    }


    private void connectDialog(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final AlertDialog dialog = builder.create();
        View dialogView = View.inflate(this, R.layout.dialog_set_customname, null);
        dialog.setView(dialogView);
        dialog.show();

        final EditText et_customName = dialogView.findViewById(R.id.et_customName);

        final Button btn_adjust = dialogView.findViewById(R.id.btn_adjust);
        final Button btn_cancel = dialogView.findViewById(R.id.btn_cancel);

        btn_adjust.setOnClickListener(view -> {
            String name = et_customName.getText().toString();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "未命名", Toast.LENGTH_SHORT).show();
                return;
            }
            firstConnect(name, file);
            Toast.makeText(this, "设备命名为：" + name, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btn_cancel.setOnClickListener(view -> dialog.dismiss());
    }

    private void startUploadDialog() {
        result_judge.post(() -> result_judge.setText(""));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final AlertDialog dialog = builder.create();
        View dialogView = View.inflate(this, R.layout.dialog_startupload, null);
        dialog.setView(dialogView);
        dialog.show();

        final EditText et_account = dialogView.findViewById(R.id.et_account);
        final EditText et_password = dialogView.findViewById(R.id.et_password);

        final Button btn_adjust = dialogView.findViewById(R.id.btn_adjust);
        final Button btn_cancel = dialogView.findViewById(R.id.btn_cancel);

        btn_adjust.setOnClickListener(view -> {
            String account = et_account.getText().toString();
            uid = account;
            String password = et_password.getText().toString();
            this.password = password;
            if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "请输入账号及密码", Toast.LENGTH_SHORT).show();
            } else {
                Call<Boolean> call = netService.startUploadPattern(account, password);
                act_net_startUpload(call, dialog);
            }
        });

        btn_cancel.setOnClickListener(view -> dialog.dismiss());
    }

    private void act_net_startUpload(Call<Boolean> call, AlertDialog dialog) {
        call.enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(@NonNull Call<Boolean> call, @NonNull Response<Boolean> response) {
                Boolean result = response.body();
                if (Boolean.TRUE.equals(result)) {
                    uploadPattern = true;
                    bt_change.post(() -> bt_change.setImageResource(R.drawable.uploading_icon));
                    Toast.makeText(MainActivity.this, "已进入录入模式", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(MainActivity.this, "账号或密码错误", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Boolean> call, @NonNull Throwable t) {

            }
        });
    }

    private void firstConnect(String name, File file) {
        boolean isCreated = false;
        try {
            isCreated = file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (isCreated) {
            Call<Integer> call = netService.initDevice(name);
            call.enqueue(new Callback<Integer>() {
                @Override
                public void onResponse(@NonNull Call<Integer> call, @NonNull Response<Integer> response) {
                    int temp_id = response.body();
                    try (FileOutputStream fos = getApplicationContext().openFileOutput(filename, Context.MODE_PRIVATE)) {
                        fos.write(Integer.valueOf(temp_id).toString().getBytes(StandardCharsets.UTF_8));
                        fos.flush();
                        fos.close();
                        did = temp_id;
                        Log.i("InitSuccess", "设备注册成功" + did);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Integer> call, @NonNull Throwable t) {
                    Log.i("NetWrong", "请求设备id错误");
                }
            });
        } else {
            Log.i("LocalWrong", "初始化文件错误");
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
                    bitmap = MyUtils.myCreateBitmap(image);
                    //check face
                    FaceDetector face_detector = new FaceDetector(bitmap.getWidth(), bitmap.getHeight(), 1);
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
                        if (coverView.allAccepted()) {
                            if (!working && !uploadPattern) {
                                try {
                                    MyUtils.saveBitmap(bitmap, -2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                @SuppressLint("SdCardPath") File file = new File("/sdcard/DCIM/Camera/-2.jpg");
                                RequestBody body = RequestBody.create(MediaType.parse("multipart/form-data"), file);

                                MultipartBody multipartBody = new MultipartBody.Builder()
                                        .addFormDataPart("file", "-2.jpg", body)
                                        .setType(MultipartBody.FORM)
                                        .build();
                                Call<String> call = netService.FaceRecognition(multipartBody.part(0));
                                working = true;
                                act_net_faceRecognition(call);
                            }
                        }
                    } else {
                        working = false;
                        result_judge.post(() -> result_judge.setText(""));
                        passNum = 0;
                        coverView.focusLost();
                    }
                    coverView.invalidate();
                    imageProxy.close();
                }
        );
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
    }

    private void act_net_faceRecognition(Call<String> call) {
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                int pid = Integer.parseInt(response.body());
                if (pid == 0)
                    result_judge.post(() -> result_judge.setText("不通过"));
                else {
                    Call<Boolean> netCall = netService.judgeAndRecord(pid, did);
                    act_net_judgeAndRecord(netCall);
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {

            }
        });
    }

    private void act_net_judgeAndRecord(Call<Boolean> netCall) {
        netCall.enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(@NonNull Call<Boolean> call, @NonNull Response<Boolean> response) {
                Boolean power = response.body();
                if (Boolean.TRUE.equals(power)) {
                    result_judge.post(() -> result_judge.setText("通过"));
                } else {
                    result_judge.post(() -> result_judge.setText("无权通过"));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Boolean> call, @NonNull Throwable t) {

            }
        });
    }

    /**
     * add coverView
     */
    void addCover(View view) {
        coverView = new CoverView(this);
        ((FrameLayout) view).addView(coverView);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initPreview();
            } else {
                finish();
            }
        }
    }


}
