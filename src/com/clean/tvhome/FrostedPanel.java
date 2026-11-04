package com.clean.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.FrameLayout;

/**
 * A rounded "glass" panel: draws the blurred wallpaper that sits behind it, a tint and a
 * hairline border. Real blur needs Android 12, which Fire TV doesn't have, so we
 * use a pre-blurred copy of the wallpaper instead.
 */
final class FrostedPanel extends FrameLayout {
    static Bitmap backdrop;

    private final float radius;
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final Rect dst = new Rect();
    private final int[] loc = new int[2];

    FrostedPanel(Context c, float radius, int tintColor, int borderColor, float dp) {
        super(c);
        this.radius = radius;
        tint.setColor(tintColor);
        border.setColor(borderColor);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1f, dp));
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void onDraw(Canvas c) {
        box.set(0, 0, getWidth(), getHeight());
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        if (backdrop != null) {
            getLocationOnScreen(loc);
            int sw = getRootView().getWidth(), sh = getRootView().getHeight();
            dst.set(-loc[0], -loc[1], sw - loc[0], sh - loc[1]);
            c.drawBitmap(backdrop, null, dst, image);
        }
        c.drawRoundRect(box, radius, radius, tint);
        c.restore();
        box.inset(0.5f, 0.5f);
        c.drawRoundRect(box, radius, radius, border);
    }
}
