package com.kyunghoon.eversolohistory;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(15, 17, 19);
    private static final int SURFACE = Color.rgb(34, 38, 42);
    private static final int GOLD = Color.rgb(218, 187, 108);
    private static final int TEXT = Color.rgb(239, 241, 242);
    private static final int MUTED = Color.rgb(155, 163, 170);
    private static final int LABEL = Color.rgb(126, 137, 146);
    private static final int SUCCESS = Color.rgb(126, 196, 154);

    private TextView status;
    private TextView track;
    private TextView count;
    private TextView error;
    private TextView storage;
    private final Handler handler = new Handler();
    private HistoryDb db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new HistoryDb(this);
        setContentView(buildUi());
        requestStoragePermissionIfNeeded();
        startMonitor();
        handler.post(refreshLoop);
    }

    private View buildUi() {
        int p = dp(26);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);
        root.setBackgroundColor(BG);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("EVERSOLO · LOCAL PLAYBACK");
        eyebrow.setTextSize(11);
        eyebrow.setLetterSpacing(0.12f);
        eyebrow.setTextColor(GOLD);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(eyebrow);

        TextView title = new TextView(this);
        title.setText("Play History");
        title.setTextSize(31);
        title.setTextColor(TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(2), 0, 0);
        root.addView(title);

        TextView version = new TextView(this);
        version.setText("v0.2.1 Patch");
        version.setTextSize(14);
        version.setTextColor(MUTED);
        version.setPadding(0, dp(2), 0, dp(18));
        root.addView(version);

        TextView sub = new TextView(this);
        sub.setText("DMP-A6 playback monitor\n127.0.0.1:9529 · fallback 192.168.1.9");
        sub.setTextSize(14);
        sub.setTextColor(MUTED);
        sub.setLineSpacing(0, 1.15f);
        sub.setPadding(0, 0, 0, dp(22));
        root.addView(sub);

        status = field(root, "STATUS");
        track = field(root, "CURRENT TRACK");
        count = field(root, "SAVED SESSIONS");
        storage = field(root, "PERSISTENT DATABASE");
        error = field(root, "LAST ERROR");

        TextView actionTitle = new TextView(this);
        actionTitle.setText("CONTROLS");
        actionTitle.setTextSize(12);
        actionTitle.setTextColor(LABEL);
        actionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        actionTitle.setPadding(0, dp(8), 0, dp(6));
        root.addView(actionTitle);

        Button start = button("Start monitoring", true);
        start.setOnClickListener(v -> startMonitor());
        root.addView(start);

        Button stop = button("Stop monitoring", false);
        stop.setOnClickListener(v -> {
            getSharedPreferences(HistoryService.PREF, MODE_PRIVATE).edit()
                    .putBoolean(HistoryService.KEY_ENABLED, false).apply();
            stopService(new Intent(this, HistoryService.class));
            Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show();
        });
        root.addView(stop);

        Button export = button("Export CSV now", true);
        export.setOnClickListener(v -> exportCsv());
        root.addView(export);

        Button retryStorage = button("Retry music-disk storage", false);
        retryStorage.setOnClickListener(v -> {
            db.ensureMirror();
            exportCsv();
        });
        root.addView(retryStorage);

        TextView note = new TextView(this);
        note.setText("STORAGE\n/.EversoloManager/play_history.db\n/.EversoloManager/play_history.csv\n\nPLAY RULES\n30 sec or 50% = qualified\nUnder 10 sec = skipped\n90% position = completed");
        note.setTextSize(13);
        note.setTextColor(MUTED);
        note.setLineSpacing(0, 1.18f);
        note.setPadding(0, dp(22), 0, dp(10));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.addView(root);
        return scroll;
    }

    private TextView field(LinearLayout root, String label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setLetterSpacing(0.08f);
        l.setTextColor(LABEL);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(l);

        TextView v = new TextView(this);
        v.setText("-");
        v.setTextSize(18);
        v.setTextColor(TEXT);
        v.setPadding(0, dp(3), 0, dp(18));
        root.addView(v);
        return v;
    }

    private Button button(String text, boolean accent) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(accent ? Color.rgb(28, 25, 17) : TEXT);
        b.setBackgroundTintList(ColorStateList.valueOf(accent ? GOLD : SURFACE));
        b.setPadding(dp(8), dp(4), dp(8), dp(4));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private void requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 50);
        }
    }

    private void startMonitor() {
        getSharedPreferences(HistoryService.PREF, MODE_PRIVATE).edit()
                .putBoolean(HistoryService.KEY_ENABLED, true).apply();
        Intent s = new Intent(this, HistoryService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
        else startService(s);
    }

    private void exportCsv() {
        try {
            db.ensureMirror();
            File f = CsvExporter.export(this, db);
            Toast.makeText(this, "CSV: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 50) {
            db.ensureMirror();
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportCsv();
            }
        }
    }

    private final Runnable refreshLoop = new Runnable() {
        @Override public void run() {
            String s = getSharedPreferences(HistoryService.PREF, MODE_PRIVATE)
                    .getString(HistoryService.KEY_STATUS, "Unknown");
            String t = getSharedPreferences(HistoryService.PREF, MODE_PRIVATE)
                    .getString(HistoryService.KEY_TRACK, "");
            String e = getSharedPreferences(HistoryService.PREF, MODE_PRIVATE)
                    .getString(HistoryService.KEY_LAST_ERROR, "");
            String storageError = db.getMirrorError();

            status.setText(s);
            status.setTextColor("Playing".equalsIgnoreCase(s) || "Running".equalsIgnoreCase(s) ? SUCCESS : TEXT);
            track.setText(t.length() == 0 ? "-" : t);
            count.setText(String.valueOf(db.getSessionCount()));
            storage.setText(db.getMirrorPath());

            if (storageError != null && storageError.length() > 0) {
                error.setText("Storage: " + storageError + (e.length() > 0 ? "\nAPI: " + e : ""));
                error.setTextColor(Color.rgb(226, 137, 137));
            } else {
                error.setText(e.length() == 0 ? "-" : e);
                error.setTextColor(e.length() == 0 ? TEXT : Color.rgb(226, 137, 137));
            }
            handler.postDelayed(this, 1000);
        }
    };

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refreshLoop);
        super.onDestroy();
    }
}
