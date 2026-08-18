package com.xnat.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

final class CapsuleProgressView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = 0f;
    private int trackColor = 0xffd7dfeb;
    private int fillColor = 0xff166fff;

    CapsuleProgressView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setProgressFraction(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    void setColors(int track, int fill) {
        trackColor = track;
        fillColor = fill;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = h / 2f;
        paint.setColor(trackColor);
        canvas.drawRoundRect(new RectF(0, 0, w, h), r, r, paint);
        float fw = w * progress;
        if (fw <= 0f) return;
        paint.setColor(fillColor);
        if (fw <= h) {
            canvas.drawCircle(fw / 2f, h / 2f, fw / 2f, paint);
        } else {
            canvas.drawRoundRect(new RectF(0, 0, fw, h), r, r, paint);
        }
    }
}
