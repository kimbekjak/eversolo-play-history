# Eversolo Play History v0.1

Purpose-built playback history logger for Eversolo DMP-A6.

## Confirmed A6 API behavior

Endpoint used by the app:

- Primary (on the A6 itself): `http://127.0.0.1:9529/ZidooMusicControl/v2/getState`
- Fallback: `http://192.168.1.9:9529/ZidooMusicControl/v2/getState`

Observed on DMP-A6 firmware v1.5.75:

- `state=3` while playing
- `position` and `duration` are milliseconds
- `playingMusic` includes `id`, `title`, `artist`, `album`, `extension`, `codec`, and `sampleRate`
- `everSoloPlayInfo.playStatus=1` while playing
- `everSoloPlayInfo.playTypeSubtitle=LOCAL` for local playback

## v0.1 behavior

- Runs as a foreground service on the A6 itself
- Polls playback state every second
- Automatically resumes after device reboot
- Uses SQLite as the authoritative history database
- Automatically refreshes CSV after each closed listening session
- Pauses do not split a session
- Stop/idle closes a session after 5 seconds
- Qualified play: >=30 seconds OR >=50% of duration
- Skip: <10 seconds of actual listening time
- Completed: >=90% position reached

## Storage

Primary CSV export target:

`/sdcard/EversoloHistory/history.csv`

If Android blocks public external storage, the exporter falls back to the app's external-files folder.

The SQLite DB remains in Android app-internal storage:

`eversolo_history.db`

## Building

This source is intentionally dependency-light. Android Studio can open the root folder and build `app` directly.

A GitHub Actions workflow is also included. It installs Gradle 8.7 and builds a debug APK automatically.

## First installation

Launch the app once after sideloading. It starts the monitor immediately. After that, the boot receiver will restart monitoring whenever the A6 boots, unless monitoring was explicitly stopped from the app.
