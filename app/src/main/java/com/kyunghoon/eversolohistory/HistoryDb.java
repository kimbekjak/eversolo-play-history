package com.kyunghoon.eversolohistory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;

public class HistoryDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "eversolo_history.db";
    private static final int DB_VERSION = 1;

    private final Context context;
    private SQLiteDatabase mirrorDb;
    private String mirrorPath = "";
    private String mirrorError = "";

    public HistoryDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
        getWritableDatabase();
        ensureMirror();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        ensureSchema(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        ensureSchema(db);
    }

    private void ensureSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sessions (" +
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
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_music ON sessions(music_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_started ON sessions(started_at)");
    }

    public synchronized void ensureMirror() {
        try {
            File manager = StorageLocator.managerDir(context);
            File dbFile = new File(manager, "play_history.db");
            mirrorPath = dbFile.getAbsolutePath();
            if (mirrorDb != null && mirrorDb.isOpen()) mirrorDb.close();
            mirrorDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
            ensureSchema(mirrorDb);

            SQLiteDatabase internal = getWritableDatabase();
            if (rowCount(internal) == 0 && rowCount(mirrorDb) > 0) {
                copyRows(mirrorDb, internal);
            }
            copyRows(internal, mirrorDb);
            mirrorError = "";
        } catch (Exception e) {
            mirrorDb = null;
            mirrorError = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
        }
    }

    public synchronized long insertSession(Session s) {
        SQLiteDatabase internal = getWritableDatabase();
        long id = internal.insert("sessions", null, valuesFor(s, false, 0));
        if (mirrorDb == null || !mirrorDb.isOpen()) ensureMirror();
        if (mirrorDb != null && mirrorDb.isOpen()) {
            ContentValues v = valuesFor(s, true, id);
            mirrorDb.insertWithOnConflict("sessions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
        return id;
    }

    private ContentValues valuesFor(Session s, boolean includeId, long id) {
        ContentValues v = new ContentValues();
        if (includeId && id > 0) v.put("_id", id);
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
        return v;
    }

    private int rowCount(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM sessions", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private void copyRows(SQLiteDatabase src, SQLiteDatabase dst) {
        Cursor c = src.rawQuery(
                "SELECT _id,music_id,title,artist,album,extension,codec,sample_rate,source," +
                        "started_at,ended_at,listened_ms,max_position_ms,duration_ms,completed,qualified,skipped FROM sessions", null);
        dst.beginTransaction();
        try {
            while (c.moveToNext()) {
                ContentValues v = new ContentValues();
                v.put("_id", c.getLong(0));
                v.put("music_id", c.getLong(1));
                v.put("title", c.getString(2));
                v.put("artist", c.getString(3));
                v.put("album", c.getString(4));
                v.put("extension", c.getString(5));
                v.put("codec", c.getString(6));
                v.put("sample_rate", c.getString(7));
                v.put("source", c.getString(8));
                v.put("started_at", c.getLong(9));
                v.put("ended_at", c.getLong(10));
                v.put("listened_ms", c.getLong(11));
                v.put("max_position_ms", c.getLong(12));
                v.put("duration_ms", c.getLong(13));
                v.put("completed", c.getInt(14));
                v.put("qualified", c.getInt(15));
                v.put("skipped", c.getInt(16));
                dst.insertWithOnConflict("sessions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            dst.setTransactionSuccessful();
        } finally {
            c.close();
            dst.endTransaction();
        }
    }

    public int getSessionCount() {
        return rowCount(getReadableDatabase());
    }

    public Cursor allSessions() {
        return getReadableDatabase().rawQuery(
                "SELECT _id,music_id,title,artist,album,extension,codec,sample_rate,source," +
                        "started_at,ended_at,listened_ms,max_position_ms,duration_ms,completed,qualified,skipped " +
                        "FROM sessions ORDER BY started_at DESC", null);
    }

    public String getMirrorPath() {
        return mirrorPath.length() == 0 ? "(not available)" : mirrorPath;
    }

    public String getMirrorError() {
        return mirrorError;
    }

    @Override
    public synchronized void close() {
        if (mirrorDb != null && mirrorDb.isOpen()) mirrorDb.close();
        super.close();
    }
}
