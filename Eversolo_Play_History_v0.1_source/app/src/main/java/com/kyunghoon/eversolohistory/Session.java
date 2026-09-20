package com.kyunghoon.eversolohistory;

public class Session {
    public long musicId;
    public String title = "";
    public String artist = "";
    public String album = "";
    public String extension = "";
    public String codec = "";
    public String sampleRate = "";
    public String source = "";
    public long startedAt;
    public long endedAt;
    public long listenedMs;
    public long maxPositionMs;
    public long durationMs;
    public boolean completed;
    public boolean qualified;
    public boolean skipped;

    public String identityKey() {
        return musicId + "|" + title + "|" + artist + "|" + album;
    }
}
