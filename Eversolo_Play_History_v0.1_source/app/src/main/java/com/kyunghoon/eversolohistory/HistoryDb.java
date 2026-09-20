package com.kyunghoon.eversolohistory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class HistoryDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "eversolo_history.db";
    private static final int DB_VERSION = 1;

    public HistoryDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "music_id INTEGER," +
                "title TEXT," +
                "artist TEXT," +
                "album TEXT," +
                "extension TEXT," +
                "codec TEXT," +
                "sample_rate TEXT," +
                "source TEXT," +
                "started_at INTEGER," +
                "ended_at INTEGER," +
                "listened_ms INTEGER," +
                "max_position_ms INTEGER," +
                "duration_ms INTEGER," +
                "completed INTEGER," +
                "qualified INTEGER," +
                "skipped INTEGER" +
                ")");
        db.execSQL("CREATE INDEX idx_sessions_music ON sessions(music_id)");
        db.execSQL("CREATE INDEX idx_sessions_started ON sessions(started_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS sessions");
        onCreate(db);
    }

    public long insertSession(Session s) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("music_id", s.musicId);
        v.put("title", s.title);
        v.put("artist", s.artist);
        v.put("album", s.album);
        v.put("extension", s.extension);
        v.put("codec", s.codec);
        v.put("sample_rate", s.sampleRate);
        v.put("source", s.source);
        v.put("started_at", s.startedAt);
        v.put("ended_at", s.endedAt);
        v.put("listened_ms", s.listenedMs);
        v.put("max_position_ms", s.maxPositionMs);
        v.put("duration_ms", s.durationMs);
        v.put("completed", s.completed ? 1 : 0);
        v.put("qualified", s.qualified ? 1 : 0);
        v.put("skipped", s.skipped ? 1 : 0);
        return db.insert("sessions", null, v);
    }

    public int getSessionCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sessions", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public Cursor allSessions() {
        return getReadableDatabase().rawQuery(
                "SELECT _id,music_id,title,artist,album,extension,codec,sample_rate,source," +
                        "started_at,ended_at,listened_ms,max_position_ms,duration_ms,completed,qualified,skipped " +
                        "FROM sessions ORDER BY started_at DESC", null);
    }
}
