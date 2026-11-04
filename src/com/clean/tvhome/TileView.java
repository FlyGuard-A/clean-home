package com.clean.tvhome;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A rounded 16:9 card with a label that only shows while focused, tvOS style. */
final class TileView extends LinearLayout {
    final AppEntry entry;
    private final FrameLayout card;
    private final ImageView image;
    private final TextView label;
    private final GradientDrawable cardBg;
    private final float dp;
    private final int h;
    private ObjectAnimator wiggle;

    TileView(Context c, AppEntry entry, int w, int h, float dp) {
        super(c);
        this.entry = entry;
        this.dp = dp;
        this.h = h;
        setOrientation(VERTICAL);
        setFocusable(true);
        setClipChildren(false);
        setClipToPadding(false);

        cardBg = new GradientDrawable();
        cardBg.setCornerRadius(12 * dp);
        cardBg.setColor(0xFF22222A);

        card = new FrameLayout(c);
        card.setBackground(cardBg);
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);
        card.setElevation(3 * dp);
        card.setPivotX(w / 2f);
        card.setPivotY(h / 2f);

        image = new ImageView(c);
        image.setAlpha(0f);
        card.addView(image, new FrameLayout.LayoutParams(-1, -1));

        label = new TextView(c);
        label.setText(entry.label);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(15);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0f);
        label.setShadowLayer(6 * dp, 0, 1 * dp, 0x99000000);

        addView(card, new LayoutParams(w, h));
        LayoutParams lp = new LayoutParams(w, -2);
        lp.topMargin = (int) (10 * dp);
        addView(label, lp);
    }

    void setArt(IconLoader.Art art, boolean animate) {
        image.setImageBitmap(art.bitmap);
        if (art.banner) {
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setPadding(0, 0, 0, 0);
            cardBg.setColor(0xFF22222A);
        } else {
            int pad = (int) (h * 0.2f);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(pad, pad, pad, pad);
            cardBg.setColor(art.color);
        }
        if (animate) image.animate().alpha(1f).setDuration(160).start();
        else image.setAlpha(1f);
    }

    void setGlyph(String glyph) {
        TextView t = new TextView(getContext());
        t.setText(glyph);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(30);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        card.addView(t, new FrameLayout.LayoutParams(-1, -1));
        cardBg.setColor(0xFF2C2C35);
    }

    void showFocus(boolean focused) {
        card.animate()
                .scaleX(focused ? 1.12f : 1f)
                .scaleY(focused ? 1.12f : 1f)
                .translationZ(focused ? 18 * dp : 0)
                .setInterpolator(new DecelerateInterpolator(2f))
                .setDuration(200)
                .start();
        label.animate()
                .alpha(focused ? 1f : 0f)
                .translationY(focused ? 8 * dp : 0)
                .setDuration(200)
                .start();
    }

    void setWiggle(boolean on) {
        if (on && wiggle == null) {
            wiggle = ObjectAnimator.ofFloat(card, "rotation", -1.6f, 1.6f);
            wiggle.setDuration(130);
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.start();
        } else if (!on && wiggle != null) {
            wiggle.cancel();
            wiggle = null;
            card.setRotation(0);
        }
    }
}
