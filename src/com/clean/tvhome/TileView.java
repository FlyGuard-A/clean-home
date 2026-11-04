package com.clean.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** A rounded app tile: the app's logo on its own brand color. Grows and lights up when focused. */
final class TileView extends FrameLayout {
    final AppEntry entry;
    private final ImageView image;
    private final GradientDrawable ring;
    private final float dp;
    private ObjectAnimator wiggle;
    private ValueAnimator ringAnim;

    TileView(Context c, AppEntry entry, float dp) {
        super(c);
        this.entry = entry;
        this.dp = dp;
        setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * dp);
        bg.setColor(0x40FFFFFF);
        setBackground(bg);
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        setClipToOutline(true);
        setElevation(4 * dp);

        image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setAlpha(0f);
        addView(image, new LayoutParams(-1, -1));

        ring = new GradientDrawable();
        ring.setCornerRadius(16 * dp);
        ring.setColor(0x00000000);
        ring.setStroke((int) (3 * dp), 0xFFFFFFFF);
        ring.setAlpha(0);
        setForeground(ring);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        setPivotX(w / 2f);
        setPivotY(h / 2f);
    }

    void setArt(Bitmap b, boolean animate) {
        image.setImageBitmap(b);
        if (animate) image.animate().alpha(1f).setDuration(160).start();
        else image.setAlpha(1f);
    }

    void setGlyph(String glyph) {
        TextView t = new TextView(getContext());
        t.setText(glyph);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(28);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        addView(t, new LayoutParams(-1, -1));
    }

    void showFocus(boolean focused) {
        animate().scaleX(focused ? 1.15f : 1f)
                .scaleY(focused ? 1.15f : 1f)
                .translationZ(focused ? 20 * dp : 0)
                .setInterpolator(new DecelerateInterpolator(2f))
                .setDuration(200)
                .start();
        if (ringAnim != null) ringAnim.cancel();
        ringAnim = ValueAnimator.ofInt(ring.getAlpha(), focused ? 230 : 0);
        ringAnim.setDuration(200);
        ringAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                ring.setAlpha((Integer) a.getAnimatedValue());
            }
        });
        ringAnim.start();
    }

    void setWiggle(boolean on) {
        if (on && wiggle == null) {
            wiggle = ObjectAnimator.ofFloat(this, "rotation", -1.8f, 1.8f);
            wiggle.setDuration(130);
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.start();
        } else if (!on && wiggle != null) {
            wiggle.cancel();
            wiggle = null;
            setRotation(0);
        }
    }
}
