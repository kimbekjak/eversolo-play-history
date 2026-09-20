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
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

    private ScheduledExecutorService executor;
    private HistoryDb db;
    private SharedPreferences prefs;
    private Session current;
    private boolean previousPollWasPlaying = false;
    private long lastPollElapsed = 0;
    private long nonPlayingSinceElapsed = 0;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        db = new HistoryDb(this);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        createChannel();
        startForeground(NOTIFY_ID, buildNotification("Monitoring A6 playback"));
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EversoloHistory:Monitor");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
        prefs.edit().putString(KEY_STATUS, "Running").apply();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(this::pollSafe, 0, 1, TimeUnit.SECONDS);
        }
        return START_STICKY;
    }

    private void pollSafe() {
        try {
            JSONObject root = fetchJson(API_LOCAL);
            if (root == null) root = fetchJson(API_FALLBACK);
            if (root == null) throw new Exception("No response from A6 API");
            handleState(root);
            prefs.edit().putString(KEY_LAST_ERROR, "").apply();
        } catch (Exception e) {
            prefs.edit().putString(KEY_STATUS, "Waiting for A6 API")
                    .putString(KEY_LAST_ERROR, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()))
                    .apply();
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

    private void handleState(JSONObject root) throws Exception {
        int state = root.optInt("state", 0);
        long position = root.optLong("position", 0);
        long duration = root.optLong("duration", 0);
        JSONObject m = root.optJSONObject("playingMusic");
        JSONObject playInfo = root.optJSONObject("everSoloPlayInfo");

        long nowElapsed = SystemClock.elapsedRealtime();
        long nowWall = System.currentTimeMillis();
        boolean playing = state == 3 && m != null;

        if (playing) {
            Session incoming = fromJson(m, playInfo, nowWall, position, duration);
            String incomingKey = incoming.identityKey();

            if (current == null) {
                current = incoming;
                lastPollElapsed = nowElapsed;
            } else if (!current.identityKey().equals(incomingKey)) {
                finalizeCurrent(nowWall);
                current = incoming;
                lastPollElapsed = nowElapsed;
            } else {
                if (previousPollWasPlaying && lastPollElapsed > 0) {
                    long delta = nowElapsed - lastPollElapsed;
                    if (delta > 0 && delta <= 2500) current.listenedMs += delta;
                }
                current.durationMs = Math.max(current.durationMs, duration);
                current.maxPositionMs = Math.max(current.maxPositionMs, position);
                lastPollElapsed = nowElapsed;
            }

            current.maxPositionMs = Math.max(current.maxPositionMs, position);
            current.durationMs = Math.max(current.durationMs, duration);
            previousPollWasPlaying = true;
            nonPlayingSinceElapsed = 0;
            prefs.edit()
                    .putString(KEY_STATUS, "Playing")
                    .putString(KEY_TRACK, current.artist + " — " + current.title)
                    .apply();
            return;
        }

        previousPollWasPlaying = false;
        lastPollElapsed = nowElapsed;

        // Pause: keep the same session open so resume remains one listening session.
        if (state == 4 && current != null) {
            nonPlayingSinceElapsed = 0;
            prefs.edit().putString(KEY_STATUS, "Paused").apply();
            return;
        }

        // Stop / idle: close after five seconds to avoid transient state changes.
        if (current != null) {
            if (nonPlayingSinceElapsed == 0) nonPlayingSinceElapsed = nowElapsed;
            if (nowElapsed - nonPlayingSinceElapsed >= 5000) {
                finalizeCurrent(nowWall);
                nonPlayingSinceElapsed = 0;
            }
        }
        prefs.edit().putString(KEY_STATUS, "Idle").putString(KEY_TRACK, "").apply();
    }

    private Session fromJson(JSONObject m, JSONObject playInfo, long startedAt, long position, long duration) {
        Session s = new Session();
        s.musicId = m.optLong("id", -1);
        s.title = m.optString("title", "");
        s.artist = m.optString("artist", "");
        s.album = m.optString("album", "");
        s.extension = m.optString("extension", "");
        s.codec = m.optString("codec", "");
        s.sampleRate = m.optString("sampleRate", "");
        s.startedAt = startedAt;
        s.maxPositionMs = position;
        s.durationMs = duration;
        s.source = playInfo != null ? playInfo.optString("playTypeSubtitle", "") : "";
        return s;
    }

    private synchronized void finalizeCurrent(long endedAt) {
        if (current == null) return;
        current.endedAt = endedAt;
        long d = current.durationMs;
        current.completed = d > 0 && current.maxPositionMs >= (long)(d * 0.90);
        current.qualified = current.listenedMs >= 30000 || (d > 0 && current.listenedMs >= (long)(d * 0.50));
        current.skipped = current.listenedMs < 10000;
        db.insertSession(current);
        try { CsvExporter.export(this, db); } catch (Exception ignored) {}
        current = null;
    }

    private Notification buildNotification(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ?
                new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return b.setContentTitle("Eversolo Play History")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Playback history monitor", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdownNow();
        finalizeCurrent(System.currentTimeMillis());
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        prefs.edit().putString(KEY_STATUS, "Stopped").apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
