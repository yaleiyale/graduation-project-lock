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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
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
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.UUID;
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
    private static final int REQUEST_CODE = 1;
    ImageAnalysis imageAnalysis;
    Preview preview;
    CoverView coverView;
    int passNum = 0;
    Bitmap bitmap;
    NetService netService;
    String filename = "did";
    TextView result_judge;
    Boolean uploadPattern = false;
    ImageButton bt_change;
    String uid = "1";
    String password = "1";
    boolean working = false;
    TCP_Server server;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private int did;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTheme(android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        netService = NetService.create();
        String ip = getIPAddress();

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
                try {
                    assert fis != null;
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                did = Integer.parseInt(contents);
                Call<Boolean> call = netService.findDevice(did, ip);
                call.enqueue(new Callback<Boolean>() {
                    @Override
                    public void onResponse(@NonNull Call<Boolean> call, @NonNull Response<Boolean> response) {
                        Boolean isfind = response.body();
                        if (Boolean.TRUE.equals(isfind)) {
                            Log.i("InitSuccess", "设备注册成功" + did);
                            Toast.makeText(MainActivity.this, "已注册！", Toast.LENGTH_SHORT).show();
                        } else {
                            if (file.delete())
                                connect();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Boolean> call, @NonNull Throwable t) {

                    }
                });
            }
        } else {
            connect();
        }


        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        View rootView = this.getWindow().getDecorView();
        addCover(rootView);


//        bt_register = findViewById(R.id.connectButton);
//        bt_register.setOnClickListener(l -> connect());

        bt_change = findViewById(R.id.changeButton);
        bt_change.setOnClickListener(l -> changePattern());


        result_judge = findViewById(R.id.text_result_judge);


        new Thread(() -> {
            server = new TCP_Server();
            try {
                server.openDoor(result_judge);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
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
            working = true;
            runOnUiThread(() -> {
                if (!uploadPattern) {
                    Toast.makeText(MainActivity.this, "未启动录入模式", Toast.LENGTH_SHORT).show();
                } else {
                    if (!coverView.shoot()) {
                        Toast.makeText(MainActivity.this, "摄取失败，请在识别成功后进行摄取", Toast.LENGTH_SHORT).show();
                    } else {
                        Bitmap clearFace = bitmap;
                        boolean pass;
                        try {
                            pass = MyUtils.evaluateClarity(clearFace);
                            if (pass) {
                                // Toast.makeText(MainActivity.this, "摄取成功", Toast.LENGTH_SHORT).show();
                                UploadDialog(clearFace);
                            } else {
                                result_judge.post(() -> result_judge.setText("图像不够清晰，请重新拍摄"));
                                working = false;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });


        }
    }

    private void UploadDialog(Bitmap faceBitmap) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            final AlertDialog dialog = builder.create();
            View dialogView = View.inflate(MainActivity.this, R.layout.upload, null);
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
                    Toast.makeText(MainActivity.this, "请输入个人姓名", Toast.LENGTH_SHORT).show();
                    return;
                }
                upload(faceBitmap, name);
                Toast.makeText(MainActivity.this, "上传成功", Toast.LENGTH_SHORT).show();
                working = false;
                dialog.dismiss();
            });

            btn_cancel.setOnClickListener(view -> {
                dialog.dismiss();
                working = false;
            });
        });

    }

    private void upload(Bitmap faceBitmap, String name) {

        Call<Integer> call = netService.generatePID(name);
        call.enqueue(new Callback<Integer>() {
            @Override
            public void onResponse(@NonNull Call<Integer> call, @NonNull Response<Integer> response) {
                int pid = response.body();
                try {
                    MyUtils.saveBitmap(faceBitmap, String.valueOf(pid));
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
        coverView.bm_left = null;
        coverView.bm_right = null;
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
        final AlertDialog dialog = builder
                .setCancelable(false)
                .create();
        View dialogView = View.inflate(this, R.layout.dialog_set_customname, null);
        dialog.setView(dialogView);
        dialog.show();

        final EditText et_customName = dialogView.findViewById(R.id.et_customName);

        final Button btn_adjust = dialogView.findViewById(R.id.btn_adjust);
        //final Button btn_cancel = dialogView.findViewById(R.id.btn_cancel);

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

        //btn_cancel.setOnClickListener(view -> dialog.dismiss());
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
            String ip = getIPAddress();
            Call<Integer> call = netService.initDevice(name, ip);
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
                            String uuid = UUID.randomUUID().toString();
                            if (!working && !uploadPattern) {
                                try {
                                    MyUtils.saveBitmap(bitmap, uuid);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                @SuppressLint("SdCardPath") File file = new File("/sdcard/DCIM/Camera/" + uuid + ".jpg");
                                RequestBody body = RequestBody.create(MediaType.parse("multipart/form-data"), file);

                                MultipartBody multipartBody = new MultipartBody.Builder()
                                        .addFormDataPart("file", uuid + ".jpg", body)
                                        .setType(MultipartBody.FORM)
                                        .build();
                                Call<String> call = netService.FaceRecognition(multipartBody.part(0));
                                working = true;
                                act_net_faceRecognition(call);
                            }
                            if (!working && uploadPattern) {
                                try {
                                    uploadPerson();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
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
                assert response.body() != null;
                int pid = Integer.parseInt(response.body());
                Call<Boolean> netCall = netService.judgeAndRecord(pid, did);
                act_net_judgeAndRecord(netCall);

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

    String getIPAddress() {
        Context context = MainActivity.this;
        NetworkInfo info = ((ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {//2G/3G/4G
                try {
                    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                        NetworkInterface intf = en.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {//wifi
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                return intIP2StringIP(wifiInfo.getIpAddress());
            }
        } else {
            Toast.makeText(MainActivity.this, "无网络", Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
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
