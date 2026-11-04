package com.clean.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A small read-only card showing what Spotify is playing. Hidden when nothing is. */
final class NowPlaying implements MediaSessionManager.OnActiveSessionsChangedListener {
    private static final Set<String> SPOTIFY = new HashSet<>(Arrays.asList(
            "com.spotify.tv.android",
            "com.spotify.music"));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private final LinearLayout card;
    private final ImageView art;
    private final TextView title, artist;
    private MediaController controller;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            attach(null);
        }
    };

    NowPlaying(Context c, float dp) {
        sessions = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(c, MediaListener.class);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(14 * dp);
        bg.setColor(0x73000000);
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(bg);
        card.setPadding((int) (8 * dp), (int) (8 * dp), (int) (18 * dp), (int) (8 * dp));
        card.setAlpha(0f);
        card.setVisibility(View.GONE);

        GradientDrawable artBg = new GradientDrawable();
        artBg.setCornerRadius(6 * dp);
        artBg.setColor(0x33FFFFFF);
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setBackground(artBg);
        art.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        art.setClipToOutline(true);
        card.addView(art, new LinearLayout.LayoutParams((int) (44 * dp), (int) (44 * dp)));

        LinearLayout lines = new LinearLayout(c);
        lines.setOrientation(LinearLayout.VERTICAL);
        title = line(c, 14, "sans-serif-medium", 0xFFFFFFFF, dp);
        artist = line(c, 12, "sans-serif", 0xB3FFFFFF, dp);
        lines.addView(title);
        lines.addView(artist);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = (int) (12 * dp);
        card.addView(lines, lp);
    }

    private static TextView line(Context c, float sp, String family, int color, float dp) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMaxWidth((int) (240 * dp));
        return t;
    }

    View view() {
        return card;
    }

    void start() {
        try {
            sessions.addOnActiveSessionsChangedListener(this, listener, handler);
            onActiveSessionsChanged(sessions.getActiveSessions(listener));
        } catch (SecurityException notAllowedYet) {
            attach(null);
        }
    }

    void stop() {
        try {
            sessions.removeOnActiveSessionsChangedListener(this);
        } catch (RuntimeException ignored) {
        }
        attach(null);
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> list) {
        MediaController found = null;
        if (list != null) {
            for (MediaController c : list) {
                if (SPOTIFY.contains(c.getPackageName())) {
                    found = c;
                    break;
                }
            }
        }
        attach(found);
    }

    private void attach(MediaController c) {
        if (controller != null) controller.unregisterCallback(callback);
        controller = c;
        if (c != null) c.registerCallback(callback, handler);
        render();
    }

    private void render() {
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        PlaybackState st = controller == null ? null : controller.getPlaybackState();
        int s = st == null ? PlaybackState.STATE_NONE : st.getState();
        boolean playing = s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING;
        boolean paused = s == PlaybackState.STATE_PAUSED;

        if (md == null || !(playing || paused)) {
            card.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                @Override
                public void run() {
                    card.setVisibility(View.GONE);
                }
            }).start();
            return;
        }
        title.setText(md.getString(MediaMetadata.METADATA_KEY_TITLE));
        artist.setText(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
        Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        art.setImageBitmap(b);
        card.setVisibility(View.VISIBLE);
        card.animate().alpha(playing ? 1f : 0.55f).setDuration(250).withEndAction(null).start();
    }
}
