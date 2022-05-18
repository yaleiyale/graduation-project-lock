package com.example.lock;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface NetService {


    @POST("/getdeviceid")
    @FormUrlEncoded
    Call<Integer> initDevice(@Field("customName") String customName);

    @POST("/getidbybitmap")
    @Multipart
    Call<String> FaceRecognition(@Part MultipartBody.Part file);

    @POST("/upload")
    @Multipart
    Call<String> upload(@Part MultipartBody.Part file);

    @POST("/generatepid")
    @FormUrlEncoded
    Call<Integer> generatePID(@Field("name") String name);


    @POST("/judgeandrecord")
    @FormUrlEncoded
    Call<Boolean> judgeAndRecord(@Field("pid") int pid, @Field("did") int did);


    @POST("/getuserlist")
    @FormUrlEncoded
    Call<Boolean> startUploadPattern(@Field("account") String account, @Field("password") String password);

    static NetService create() {
        return new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("http://10.241.125.154:8080")
                .build().create(NetService.class);
    }
}
