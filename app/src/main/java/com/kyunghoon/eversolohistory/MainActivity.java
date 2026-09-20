package com.kyunghoon.eversolohistory;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
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
    private TextView status;
    private TextView track;
    private TextView count;
    private TextView error;
    private final Handler handler = new Handler();
    private HistoryDb db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new HistoryDb(this);
        setContentView(buildUi());
        startMonitor();
        handler.post(refreshLoop);
    }

    private View buildUi() {
        int p = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("Eversolo Play History v0.1");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("DMP-A6 local playback monitor\nAPI: 127.0.0.1:9529 (fallback 192.168.1.9)");
        sub.setTextSize(15);
        sub.setPadding(0, dp(12), 0, dp(20));
        root.addView(sub);

        status = field(root, "Status");
        track = field(root, "Current track");
        count = field(root, "Saved sessions");
        error = field(root, "Last API error");

        Button start = button("Start monitoring");
        start.setOnClickListener(v -> startMonitor());
        root.addView(start);

        Button stop = button("Stop monitoring");
        stop.setOnClickListener(v -> {
            getSharedPreferences(HistoryService.PREF, MODE_PRIVATE).edit()
                    .putBoolean(HistoryService.KEY_ENABLED, false).apply();
            stopService(new Intent(this, HistoryService.class));
            Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show();
        });
        root.addView(stop);

        Button export = button("Export CSV now");
        export.setOnClickListener(v -> exportCsv());
        root.addView(export);

        TextView note = new TextView(this);
        note.setText("Storage:\n• SQLite database stays inside the A6 app storage.\n• CSV is automatically refreshed after each completed listening session.\n• Primary CSV path: /sdcard/EversoloHistory/history.csv\n\nRules:\n• state=3 = listening time accumulates\n• state=4 = paused, session stays open\n• idle/stop >5 sec = session closes\n• qualified play = 30 sec OR 50% of track\n• skip = under 10 sec\n• completed = 90% position reached");
        note.setTextSize(14);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private TextView field(LinearLayout root, String label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(l);
        TextView v = new TextView(this);
        v.setText("-");
        v.setTextSize(18);
        v.setPadding(0, 0, 0, dp(16));
        root.addView(v);
        return v;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private void startMonitor() {
        getSharedPreferences(HistoryService.PREF, MODE_PRIVATE).edit()
                .putBoolean(HistoryService.KEY_ENABLED, true).apply();
        Intent s = new Intent(this, HistoryService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
        else startService(s);
    }

    private void exportCsv() {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 50);
            return;
        }
        try {
            File f = CsvExporter.export(this, db);
            Toast.makeText(this, "CSV: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 50 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            exportCsv();
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
            status.setText(s);
            track.setText(t.length() == 0 ? "-" : t);
            count.setText(String.valueOf(db.getSessionCount()));
            error.setText(e.length() == 0 ? "-" : e);
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
