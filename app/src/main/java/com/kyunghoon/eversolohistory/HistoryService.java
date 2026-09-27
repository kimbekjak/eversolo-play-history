package com.kyunghoon.eversolohistory;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HistoryService extends Service {
    public static final String PREF = "history_pref";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_STATUS = "status";
    public static final String KEY_TRACK = "track";
    public static final String KEY_LAST_ERROR = "last_error";

    private static final int NOTIFY_ID = 755;
    private static final String CHANNEL_ID = "history_monitor";
    private static final String API_LOCAL = "http://127.0.0.1:9529/ZidooMusicControl/v2/getState";
    private static final String API_FALLBACK = "http://192.168.1.9:9529/ZidooMusicControl/v2/getState";

    // v0.2.3 Full-play counter:
    // - no fixed 1s/10s polling while a track is playing
    // - discover a track once, then sleep until its 90% point
    // - only completed/full plays are persisted
    private static final double COMPLETE_RATIO = 0.90;
    private static final long START_WINDOW_MS = 15000;
    private static final long MIN_PROBE_DELAY_MS = 3000;
    private static final long IDLE_RETRY_MS = 60000;
    private static final long PAUSE_RETRY_MS = 60000;
    private static final long AFTER_END_MARGIN_MS = 2500;
    private static final long UNKNOWN_DURATION_RETRY_MS = 60000;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledProbe;
    private HistoryDb db;
    private SharedPreferences prefs;
    private Session current;
    private boolean currentSeenNearStart = false;
    private String lastCountedKey = "";
    private long lastCountedStartedAt = 0;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        db = new HistoryDb(this);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        createChannel();
        startForeground(NOTIFY_ID, buildNotification("Full-play counter"));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EversoloHistory:FullPlay");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        prefs.edit().putString(KEY_STATUS, "Running").apply();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        scheduleProbe(0);
        return START_STICKY;
    }

    private synchronized void scheduleProbe(long delayMs) {
        if (executor == null || executor.isShutdown()) return;
        if (scheduledProbe != null && !scheduledProbe.isDone()) {
            scheduledProbe.cancel(false);
        }
        scheduledProbe = executor.schedule(this::probeSafe,
                Math.max(0, delayMs), TimeUnit.MILLISECONDS);
    }

    private void probeSafe() {
        try {
            JSONObject root = fetchJson(API_LOCAL);
            if (root == null) root = fetchJson(API_FALLBACK);
            if (root == null) throw new Exception("No response from A6 API");
            handleState(root);
            prefs.edit().putString(KEY_LAST_ERROR, "").apply();
        } catch (Exception e) {
            prefs.edit()
                    .putString(KEY_STATUS, "Waiting for A6 API")
                    .putString(KEY_LAST_ERROR, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()))
                    .apply();
            scheduleProbe(IDLE_RETRY_MS);
        }
    }

    private JSONObject fetchJson(String endpoint) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setConnectTimeout(1200);
            c.setReadTimeout(1200);
            c.setRequestMethod("GET");
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line);
            r.close();
            return new JSONObject(b.toString());
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void handleState(JSONObject root) {
        int state = root.optInt("state", 0);
        long position = Math.max(0, root.optLong("position", 0));
        long duration = Math.max(0, root.optLong("duration", 0));
        JSONObject m = root.optJSONObject("playingMusic");
        JSONObject playInfo = root.optJSONObject("everSoloPlayInfo");

        long nowWall = System.currentTimeMillis();
        boolean playing = state == 3 && m != null;

        if (!playing) {
            if (state == 4) {
                prefs.edit().putString(KEY_STATUS, "Paused").apply();
                scheduleProbe(PAUSE_RETRY_MS);
            } else {
                current = null;
                currentSeenNearStart = false;
                prefs.edit()
                        .putString(KEY_STATUS, "Idle")
                        .putString(KEY_TRACK, "")
                        .apply();
                scheduleProbe(IDLE_RETRY_MS);
            }
            return;
        }

        Session incoming = fromJson(m, playInfo, nowWall, position, duration);
        String incomingKey = incoming.identityKey();

        if (current == null || !current.identityKey().equals(incomingKey)) {
            // A different track means the previous one was either skipped or changed.
            // The previous track is deliberately discarded: v0.2.3 stores only full plays.
            current = incoming;
            currentSeenNearStart = position <= START_WINDOW_MS;
        } else {
            current.maxPositionMs = Math.max(current.maxPositionMs, position);
            current.durationMs = Math.max(current.durationMs, duration);
            current.listenedMs = Math.max(current.listenedMs, position);
        }

        prefs.edit()
                .putString(KEY_STATUS, currentSeenNearStart ? "Tracking full play" : "Watching current track")
                .putString(KEY_TRACK, current.artist + " — " + current.title)
                .apply();

        if (duration <= 0) {
            scheduleProbe(UNKNOWN_DURATION_RETRY_MS);
            return;
        }

        long threshold = (long) (duration * COMPLETE_RATIO);

        if (position >= threshold) {
            if (currentSeenNearStart && !alreadyCounted(current)) {
                saveCompletedPlay(nowWall, position, duration);
            }

            // Do not keep querying during the last 10%. Sleep until this track should
            // have naturally ended, then discover the next track once.
            long remaining = Math.max(0, duration - position);
            current = null;
            currentSeenNearStart = false;
            scheduleProbe(Math.max(MIN_PROBE_DELAY_MS, remaining + AFTER_END_MARGIN_MS));
            return;
        }

        // The only next playback API call for this track is near the 90% point.
        long toThreshold = threshold - position;
        scheduleProbe(Math.max(MIN_PROBE_DELAY_MS, toThreshold + 1200));
    }

    private boolean alreadyCounted(Session s) {
        if (!s.identityKey().equals(lastCountedKey)) return false;
        return Math.abs(s.startedAt - lastCountedStartedAt) < 30000;
    }

    private void saveCompletedPlay(long endedAt, long position, long duration) {
        if (current == null) return;

        current.endedAt = endedAt;
        current.durationMs = Math.max(current.durationMs, duration);
        current.maxPositionMs = Math.max(current.maxPositionMs, position);
        current.listenedMs = Math.max(current.listenedMs, position);
        current.completed = true;
        current.qualified = true;
        current.skipped = false;

        db.insertSession(current);
        try { CsvExporter.export(this, db); } catch (Exception ignored) {}

        lastCountedKey = current.identityKey();
        lastCountedStartedAt = current.startedAt;

        prefs.edit().putString(KEY_STATUS, "Full play counted").apply();
    }

    private Session fromJson(JSONObject m, JSONObject playInfo, long observedAt, long position, long duration) {
        Session s = new Session();
        s.musicId = m.optLong("id", -1);
        s.title = m.optString("title", "");
        s.artist = m.optString("artist", "");
        s.album = m.optString("album", "");
        s.extension = m.optString("extension", "");
        s.codec = m.optString("codec", "");
        s.sampleRate = m.optString("sampleRate", "");
        s.startedAt = Math.max(0, observedAt - position);
        s.listenedMs = position;
        s.maxPositionMs = position;
        s.durationMs = duration;
        s.source = playInfo != null ? playInfo.optString("playTypeSubtitle", "") : "";
        return s;
    }

    private Notification buildNotification(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("Eversolo Play History")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Playback history monitor", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        if (scheduledProbe != null) scheduledProbe.cancel(false);
        if (executor != null) executor.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        prefs.edit().putString(KEY_STATUS, "Stopped").apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
