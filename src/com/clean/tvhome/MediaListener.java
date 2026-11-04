package com.clean.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so Android lets us see which media sessions are active (for the
 * Spotify "now playing" card). It doesn't read or act on any notifications.
 */
public class MediaListener extends NotificationListenerService {
}
