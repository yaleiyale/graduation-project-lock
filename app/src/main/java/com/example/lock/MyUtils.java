package com.example.lock;

import static org.bytedeco.opencv.global.opencv_core.CV_64F;
import static org.bytedeco.opencv.global.opencv_core.meanStdDev;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.Laplacian;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import org.bytedeco.opencv.opencv_core.Mat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MyUtils {
    public static byte[] leIntToByteArray(int i) {

        final ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE);

        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.putInt(i);

        return bb.array();

    }


    /**
     * faceDetector needs bitmap_565, convert the image to bitmap
     */
    public static Bitmap myCreateBitmap(Image image) {
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

    public static Bitmap rotateBitmap(Bitmap bitmap) {
        Matrix matrix_rotate = new Matrix();
        matrix_rotate.postRotate(-90);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix_rotate, true);
    }

    public static void saveBitmap(Bitmap faceBitmap, String pid) throws IOException {
        @SuppressLint("SdCardPath") String name = "/sdcard/DCIM/Camera/" + pid + ".jpg";
        File file = new File(name);
        FileOutputStream fos = new FileOutputStream(file);
        faceBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.close();
    }

    public static Bitmap flipBitmap(Bitmap bitmap) {
        Matrix matrix_flip = new Matrix();
        matrix_flip.postScale(-1, 1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix_flip, true);
    }

    @SuppressLint("SdCardPath")
    public static boolean evaluateClarity(Bitmap clearFace) throws IOException {
        saveBitmap(clearFace, "-1");
        Mat srcImage = imread("/sdcard/DCIM/Camera/-1.jpg");
        Mat dstImage = new Mat();
        cvtColor(srcImage, dstImage, COLOR_BGR2GRAY);
        imwrite("/sdcard/DCIM/Camera/grey.jpg", dstImage);
        Mat laplacianDstImage = new Mat();
        Laplacian(dstImage, laplacianDstImage, CV_64F);
        imwrite("/sdcard/DCIM/Camera/laplacian.jpg", laplacianDstImage);
        Mat stdDev = new Mat();
        meanStdDev(laplacianDstImage, new Mat(), stdDev);
        double value = stdDev.createIndexer().getDouble();
        return value > 15.0;
    }
}
