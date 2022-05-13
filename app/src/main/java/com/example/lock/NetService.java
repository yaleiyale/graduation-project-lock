package com.example.lock;

import android.graphics.Bitmap;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

public interface NetService {

    @POST("/getdeviceid")
    @FormUrlEncoded
    Call<Integer> initDevice(@Field("customName") String customName);

    @POST("/uploadperson")
    @FormUrlEncoded
    Call<Boolean> uploadPerson(@Field("bitmap") Bitmap bitmap, @Field("filename") String filename,
                               @Field("uid") String uid, @Field("password") String password);

    @POST("/getidbybitmap")
    @FormUrlEncoded
    Call<String> FaceRecognition(@Field("left") Bitmap bitmap, @Field("uid") String uid, @Field("password") String password);

    @POST("/judgeandrecord")
    @FormUrlEncoded
    Call<Boolean> judgeAndRecord(@Field("pid") int pid, @Field("did") int did);


    @POST("/getuserlist")
    @FormUrlEncoded
    Call<Boolean> startUpload(@Field("account") String account, @Field("password") String password);

    static NetService create() {
        return new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("http://10.240.94.83:8080")
                .build().create(NetService.class);
    }
}
