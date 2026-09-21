# Eversolo Play History v0.2 Beta

Playback-history logger for Eversolo DMP-A6. This beta is intentionally installed side-by-side with v0.1 so the working v0.1 installation does not have to be deleted while v0.2 storage is tested.

## API

- Primary: `http://127.0.0.1:9529/ZidooMusicControl/v2/getState`
- Fallback: `http://192.168.1.9:9529/ZidooMusicControl/v2/getState`

## Playback rules

- `state=3`: accumulate listening time
- `state=4`: pause, keep the session open
- idle/stop >5 seconds: close session
- qualified play: >=30 seconds OR >=50% of duration
- skip: <10 seconds listened
- completed: >=90% position reached

## v0.2 storage

The app first searches the music volume containing `Music`, including the known A6 volume `E6B9-EC04`.

Preferred persistent files:

- `/.EversoloManager/play_history.db`
- `/.EversoloManager/play_history.csv`

The app also keeps an internal SQLite copy. At startup it synchronizes the internal DB and the persistent DB. On a future device such as an A8, installing v0.2 with the same music disk can restore the internal history from `play_history.db`.

If direct music-volume access is blocked, the UI shows the fallback path/error so it can be diagnosed without losing current-session logging.

## Installation note

v0.2 uses a separate application ID from v0.1 because the original v0.1 GitHub Actions debug signing key was ephemeral. That allows v0.1 to remain installed while v0.2 is verified. Stop v0.1 monitoring before starting v0.2 to avoid duplicate logging.

From v0.2 onward the workflow caches the debug keystore with a fixed cache key so subsequent v0.2 builds can update the installed beta without changing the signing key.
