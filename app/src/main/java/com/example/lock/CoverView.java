package com.example.lock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.FaceDetector;
import android.util.DisplayMetrics;
import android.view.View;

public class CoverView extends View {
    private final Paint face_paint;
    private final Paint eye_paint;
    private boolean isIris = false;
    private boolean isFace = false;
    private boolean find = false;
    float width;
    float height;
    float density;
    FaceDetector.Face face;
    Bitmap bitmap;
    Bitmap bm_left;
    Bitmap bm_right;
    PointF f_mid;
    Point mid;
    float large_EyesDistance;
    float eyesDistance;
    Rect cut_left;
    Rect cut_right;
    Rect to_left;
    Rect to_right;

    public CoverView(Context context) {
        super(context);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        width = displayMetrics.widthPixels;
        height = displayMetrics.heightPixels;
        density = width / 480;
        face_paint = new Paint();
        face_paint.setColor(Color.WHITE);
        face_paint.setStyle(Paint.Style.STROKE);
        face_paint.setStrokeWidth(2);
        face_paint.setTextSize(50);
        eye_paint = new Paint();
        eye_paint.setColor(Color.WHITE);
        eye_paint.setStyle(Paint.Style.STROKE);
        eye_paint.setStrokeWidth(2);
        eye_paint.setTextSize(50);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (find) {
            if (isFace) {
                face_paint.setColor(Color.GREEN);
            }
            canvas.drawRect(f_mid.x - large_EyesDistance, f_mid.y - large_EyesDistance, f_mid.x + large_EyesDistance, f_mid.y + large_EyesDistance, face_paint);
            if (isIris) {
                if (eyesDistance > 180) {
                    eye_paint.setColor(Color.GREEN);
                } else {
                    eye_paint.setColor(Color.WHITE);
                }
                canvas.drawCircle(f_mid.x, f_mid.y, 5, eye_paint);
                canvas.drawRect(f_mid.x - large_EyesDistance + 45, f_mid.y - 45, f_mid.x - 22.5F, f_mid.y + 45, eye_paint);
                canvas.drawRect(f_mid.x + 22.5F, f_mid.y - 45, f_mid.x + large_EyesDistance - 45, f_mid.y + 45, eye_paint);
            }
        }
        if (bm_right != null && bm_left != null) {
            canvas.drawBitmap(bm_left, cut_left, to_left, face_paint);
            canvas.drawBitmap(bm_right, cut_right, to_right, face_paint);
        }
    }

    public void irisAccepted() {
        isIris = true;
    }

    public void faceAccepted() {
        isFace = true;
    }
    public boolean allAccepted() {
        return isFace && isIris;
    }

    public void focusLost() {
        find = false;
        isFace = false;
        isIris = false;
        face_paint.setColor(Color.WHITE);
        eye_paint.setColor(Color.WHITE);
        face = null;
    }

    public void showCover(FaceDetector.Face[] f, Bitmap b) {
        find = true;
        bitmap = b;
        FaceDetector.Face face = f[0];
        f_mid = new PointF();
        face.getMidPoint(f_mid);
        mid = new Point((int) f_mid.x, (int) f_mid.y);
        f_mid.x *= density;
        f_mid.y *= density;
        eyesDistance = face.eyesDistance();
        large_EyesDistance = eyesDistance * density * 1.25F;
        eyesDistance = eyesDistance * 1.25F;
        cut_right = new Rect(0, 0, 180, 90);
        cut_left = new Rect(0, 0, 180, 90);
        to_left = new Rect(0, (int) (height - width / 4), (int) width / 2, (int) height);
        to_right = new Rect((int) width / 2, (int) (height - width / 4), (int) width, (int) height);
    }

    public boolean shoot() {
        if (eyesDistance > 180 && isFace && isIris) {
            bm_left = Bitmap.createBitmap(bitmap, mid.x - 180, mid.y - 45, 180, 90);
            bm_right = Bitmap.createBitmap(bitmap, mid.x, mid.y - 45, 180, 90);
            return true;
        } else return false;
    }
}
