package com.kyunghoon.eversolohistory;

import android.content.Context;
import android.database.Cursor;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CsvExporter {
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static File export(Context context, HistoryDb db) throws Exception {
        File root = StorageLocator.managerDir(context);
        if (!root.exists() && !root.mkdirs()) {
            throw new Exception("Cannot create storage folder: " + root.getAbsolutePath());
        }
        File out = new File(root, "play_history.csv");
        FileWriter w = new FileWriter(out, false);
        w.write("id,music_id,title,artist,album,extension,codec,sample_rate,source,started_at,ended_at,listened_sec,max_position_sec,duration_sec,completed,qualified,skipped\n");
        Cursor c = db.allSessions();
        try {
            while (c.moveToNext()) {
                w.write(c.getLong(0) + ",");
                w.write(c.getLong(1) + ",");
                w.write(q(c.getString(2)) + ",");
                w.write(q(c.getString(3)) + ",");
                w.write(q(c.getString(4)) + ",");
                w.write(q(c.getString(5)) + ",");
                w.write(q(c.getString(6)) + ",");
                w.write(q(c.getString(7)) + ",");
                w.write(q(c.getString(8)) + ",");
                w.write(q(TS.format(new Date(c.getLong(9)))) + ",");
                w.write(q(TS.format(new Date(c.getLong(10)))) + ",");
                w.write(String.format(Locale.US, "%.1f,%.1f,%.1f,", c.getLong(11)/1000.0, c.getLong(12)/1000.0, c.getLong(13)/1000.0));
                w.write(c.getInt(14) + "," + c.getInt(15) + "," + c.getInt(16) + "\n");
            }
        } finally {
            c.close();
            w.flush();
            w.close();
        }
        return out;
    }

    private static String q(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
