# Music Player (Spotify) - HELLO Interview Framework

> **Companies**: Spotify, Apple, Microsoft (Groove Music), Amazon (Music)  
> **Difficulty**: Medium  
> **Pattern**: State Pattern (player states) + Observer (UI updates) + Strategy (repeat/shuffle modes)  
> **Time**: 35 minutes

## Table of Contents
1. [Understanding the Problem](#understanding-the-problem)
2. [Requirements](#1️⃣-requirements)
3. [Core Entities](#2️⃣-core-entities)
4. [API Design](#3️⃣-api-design)
5. [Data Flow](#4️⃣-data-flow)
6. [Design + Implementation](#5️⃣-design--implementation)
7. [Deep Dives](#6️⃣-deep-dives)

---

## Understanding the Problem

### 🎯 What is a Music Player?

A system that manages playback of audio tracks organized in playlists. The core challenge is modeling the **player state machine** (STOPPED ↔ PLAYING ↔ PAUSED) where each state dictates which operations are valid and what transitions occur. On top of that, playlist navigation (next/previous) must respect the current **repeat mode** (off, repeat-one, repeat-all) and optionally **shuffle mode**.

**Real-world**: Spotify, Apple Music, YouTube Music, Amazon Music, VLC, Windows Media Player, Groove Music (Microsoft).

### For L63 Microsoft Interview

1. **State Pattern**: Player states (STOPPED, PLAYING, PAUSED) — each state handles play/pause/stop/next/previous differently
2. **Clean state transitions**: play() in STOPPED starts from beginning, play() in PAUSED resumes from position
3. **Repeat modes**: OFF (stop at end), REPEAT_ONE (loop current song), REPEAT_ALL (loop playlist)
4. **Playlist management**: Add/remove songs, navigate next/previous with wrap-around
5. **Observer Pattern**: Notify UI/listeners on state change, track change, position update
6. **Separation of concerns**: Song (data), Playlist (collection), PlayerState (behavior), MusicPlayer (coordinator)

---

## 1️⃣ Requirements

### 🎯 Clarifying Questions
- "What playback controls?" → Play, Pause, Stop, Next, Previous
- "Repeat modes?" → Off (default), Repeat One (loop current), Repeat All (loop playlist)
- "Shuffle?" → Toggle on/off — randomize track order without changing playlist
- "What happens at end of playlist?" → Depends on repeat mode: stop (OFF), restart (REPEAT_ALL), stay on last song looped (REPEAT_ONE)
- "Multiple playlists?" → Yes, but only one plays at a time
- "Seeking within track?" → P1 (mention but defer)

### Functional Requirements (P0)
1. **Play**: Start playing current song (from beginning if stopped, from position if paused)
2. **Pause**: Pause current song, preserve playback position
3. **Stop**: Stop playback, reset position to 0
4. **Next**: Skip to next song in playlist
5. **Previous**: Go to previous song in playlist
6. **Repeat Song**: Toggle repeating the current song indefinitely
7. **Repeat Playlist**: Toggle repeating the entire playlist when it reaches the end
8. **State management**: STOPPED, PLAYING, PAUSED — with valid transitions enforced

### Functional Requirements (P1)
- Shuffle mode (randomize order)
- Seek to position within track
- Volume control
- Queue (play next / add to queue)

### Non-Functional
- Clean state transitions (no illegal state jumps)
- O(1) for play/pause/stop
- O(1) for next/previous (indexed playlist)
- Extensible for new playback modes

---

## 2️⃣ Core Entities

### Entity Relationship Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                       MusicPlayer                            │
│  - playlist: Playlist                                       │
│  - state: PlayerState (State Pattern)                       │
│  - repeatMode: RepeatMode                                   │
│  - currentPosition: int (seconds into current song)         │
│  - observers: List<PlayerObserver>                          │
│  - play() / pause() / stop()                                │
│  - next() / previous()                                      │
│  - setRepeatMode(mode)                                      │
└──────────────┬──────────────────────────────────────────────┘
               │ uses
     ┌─────────┼──────────┬──────────────┐
     ▼         ▼          ▼              ▼
┌──────────┐ ┌────────────────┐ ┌─────────────────┐ ┌──────────────┐
│  Song    │ │  Playlist      │ │ PlayerState     │ │ PlayerObserver│
│          │ │                │ │ (interface)     │ │ (interface)   │
│ - title  │ │ - songs: List  │ ├─────────────────┤ ├──────────────┤
│ - artist │ │ - currentIndex │ │ StoppedState    │ │ onStateChange│
│ - album  │ │ - next()       │ │ PlayingState    │ │ onTrackChange│
│ - duration│ │ - previous()   │ │ PausedState     │ │ onPositionUpd│
│ - genre  │ │ - getCurrent() │ └─────────────────┘ └──────────────┘
└──────────┘ │ - shuffle()    │
             │ - size()       │ ┌─────────────────┐
             └────────────────┘ │  RepeatMode     │
                                │  (enum)         │
                                ├─────────────────┤
                                │ OFF             │
                                │ REPEAT_ONE      │
                                │ REPEAT_ALL      │
                                └─────────────────┘
```

### Enum: RepeatMode
```
OFF          → Stop when playlist ends
REPEAT_ONE   → Loop current song forever (next() still skips)
REPEAT_ALL   → Restart from first song when playlist ends
```

### State Interface: PlayerState
```
Each state determines:
  - play(player)     → What happens when user presses Play
  - pause(player)    → What happens when user presses Pause
  - stop(player)     → What happens when user presses Stop
  - next(player)     → What happens when user presses Next
  - previous(player) → What happens when user presses Previous
  - getName()        → State name for logging/display
```

### State Transitions
```
              play()                    pause()
    ┌──────────┐ ──────────────→ ┌────────────┐
    │  STOPPED  │                 │  PLAYING    │
    └──────┬───┘ ←────────────── └─────┬──────┘
           │       stop()              │
           │                           │ pause()
           │       stop()              ▼
           │ ←──────────────── ┌────────────┐
           │                   │   PAUSED    │
           │                   └─────┬──────┘
           │                         │
           │                         │ play() (resume)
           │                         ▼
           │                   ┌────────────┐
           └──────────────────→│  PLAYING    │
                   play()      └────────────┘

  Valid Transitions:
    STOPPED  → play()     → PLAYING (from beginning)
    PLAYING  → pause()    → PAUSED  (preserve position)
    PLAYING  → stop()     → STOPPED (reset position)
    PAUSED   → play()     → PLAYING (resume from position)
    PAUSED   → stop()     → STOPPED (reset position)

  Invalid (no-op or throw):
    STOPPED  → pause()    → no-op (nothing to pause)
    STOPPED  → stop()     → no-op (already stopped)
    PLAYING  → play()     → no-op (already playing) OR restart
```

### Class: Song
| Attribute | Type | Description |
|-----------|------|-------------|
| id | String | Unique identifier |
| title | String | "Bohemian Rhapsody" |
| artist | String | "Queen" |
| album | String | "A Night at the Opera" |
| durationSeconds | int | 354 (5:54) |
| genre | String | "Rock" |

### Class: Playlist
| Attribute | Type | Description |
|-----------|------|-------------|
| name | String | "My Favorites" |
| songs | List\<Song\> | Ordered list of songs |
| currentIndex | int | Index of currently selected song |
| shuffleOrder | List\<Integer\> | Shuffled indices (when shuffle is on) |
| shuffleEnabled | boolean | Whether shuffle mode is active |

---

## 3️⃣ API Design

```java
class MusicPlayer {
    /** Start/resume playback */
    void play();
    
    /** Pause current playback — preserve position */
    void pause();
    
    /** Stop playback — reset position to 0 */
    void stop();
    
    /** Skip to next song (respects repeat mode) */
    void next();
    
    /** Go to previous song */
    void previous();
    
    /** Set repeat mode: OFF, REPEAT_ONE, REPEAT_ALL */
    void setRepeatMode(RepeatMode mode);
    
    /** Load a playlist for playback */
    void loadPlaylist(Playlist playlist);
    
    /** Get currently playing song */
    Song getCurrentSong();
    
    /** Get current player state name */
    String getStateName();
    
    /** Get playback position in seconds */
    int getCurrentPosition();
    
    /** Register observer for state/track changes */
    void addObserver(PlayerObserver observer);
}
```

---

## 4️⃣ Data Flow

### Scenario 1: Basic Playback (Play → Pause → Resume → Stop)

```
loadPlaylist(["Bohemian Rhapsody", "Hotel California", "Stairway to Heaven"])
State: STOPPED, currentIndex: 0, position: 0

play()
  ├─ State: STOPPED → StoppedState.play(player):
  │   ├─ currentSong = playlist.getCurrent() → "Bohemian Rhapsody"
  │   ├─ position = 0 (start from beginning)
  │   ├─ state → PLAYING
  │   └─ notify: onTrackChange("Bohemian Rhapsody")
  │               onStateChange(STOPPED → PLAYING)
  └─ ▶️ Now playing: "Bohemian Rhapsody" at 0:00

// ... 2 minutes pass, position = 120s ...

pause()
  ├─ State: PLAYING → PlayingState.pause(player):
  │   ├─ position preserved = 120s
  │   ├─ state → PAUSED
  │   └─ notify: onStateChange(PLAYING → PAUSED)
  └─ ⏸️ Paused: "Bohemian Rhapsody" at 2:00

play()
  ├─ State: PAUSED → PausedState.play(player):
  │   ├─ RESUME from position = 120s (NOT restart!)
  │   ├─ state → PLAYING
  │   └─ notify: onStateChange(PAUSED → PLAYING)
  └─ ▶️ Resumed: "Bohemian Rhapsody" at 2:00

stop()
  ├─ State: PLAYING → PlayingState.stop(player):
  │   ├─ position = 0 (RESET!)
  │   ├─ state → STOPPED
  │   └─ notify: onStateChange(PLAYING → STOPPED)
  └─ ⏹️ Stopped: "Bohemian Rhapsody", position reset to 0:00
```

### Scenario 2: Next / Previous Navigation

```
Playlist: [0: "Bohemian Rhapsody", 1: "Hotel California", 2: "Stairway to Heaven"]
State: PLAYING, currentIndex: 0, RepeatMode: OFF

next()
  ├─ PlayingState.next(player):
  │   ├─ currentIndex = 0 + 1 = 1
  │   ├─ position = 0
  │   ├─ currentSong = "Hotel California"
  │   ├─ state stays PLAYING
  │   └─ notify: onTrackChange("Hotel California")
  └─ ▶️ Now playing: "Hotel California"

next()
  ├─ currentIndex = 1 + 1 = 2
  └─ ▶️ Now playing: "Stairway to Heaven"

next() at END of playlist (RepeatMode: OFF)
  ├─ currentIndex = 2, no more songs
  ├─ RepeatMode.OFF → STOP playback
  ├─ state → STOPPED
  └─ ⏹️ Playlist ended. Stopped.

previous()
  ├─ StoppedState.previous(player):
  │   ├─ currentIndex = 2 - 1 = 1
  │   ├─ state stays STOPPED (just move cursor, don't auto-play)
  │   └─ notify: onTrackChange("Hotel California")
  └─ Current song: "Hotel California" (still STOPPED)

play() → starts "Hotel California" from 0:00
```

### Scenario 3: Repeat One (Loop Current Song)

```
State: PLAYING, currentSong: "Bohemian Rhapsody"
setRepeatMode(REPEAT_ONE)

// Song reaches end (position == duration)
onSongComplete():
  ├─ RepeatMode == REPEAT_ONE
  ├─ position = 0 (reset to beginning)
  ├─ state stays PLAYING
  ├─ SAME song restarts
  └─ 🔂 Repeating: "Bohemian Rhapsody" from 0:00

// User presses next() — STILL SKIPS even in REPEAT_ONE
next()
  ├─ REPEAT_ONE doesn't prevent manual skip
  ├─ currentIndex = 0 + 1 = 1
  └─ ▶️ Now playing: "Hotel California"
  // Hotel California will now loop (REPEAT_ONE still active)

Key insight:
  ✅ REPEAT_ONE loops on auto-complete, but next()/previous() still work
  ✅ This matches Spotify behavior — repeat single doesn't trap the user
```

### Scenario 4: Repeat All (Loop Entire Playlist)

```
Playlist: [0, 1, 2], State: PLAYING, currentIndex: 2 (last song)
setRepeatMode(REPEAT_ALL)

// Last song reaches end
onSongComplete():
  ├─ currentIndex = 2 (last song), RepeatMode == REPEAT_ALL
  ├─ currentIndex = 0 (WRAP to first song!)
  ├─ position = 0
  ├─ state stays PLAYING
  └─ 🔁 Repeat all: "Bohemian Rhapsody" from 0:00

// Playlist loops indefinitely until user changes mode or stops
```

### Scenario 5: Invalid State Transitions

```
State: STOPPED

pause()
  ├─ StoppedState.pause(player):
  │   ├─ Nothing to pause — no-op
  │   └─ print: "⚠️ Cannot pause — player is stopped"
  └─ State stays: STOPPED

stop()
  ├─ StoppedState.stop(player):
  │   ├─ Already stopped — no-op
  │   └─ State stays: STOPPED

State: PLAYING
play()
  ├─ PlayingState.play(player):
  │   ├─ Already playing — no-op (or restart track, configurable)
  │   └─ State stays: PLAYING
```

---

## 5️⃣ Design + Implementation

### Song (Immutable Data Object)

```java
class Song {
    private final String id;
    private final String title;
    private final String artist;
    private final String album;
    private final int durationSeconds;
    
    public Song(String id, String title, String artist, String album, int durationSeconds) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.durationSeconds = durationSeconds;
    }
    
    // Getters only — Song is immutable
    public String getId()            { return id; }
    public String getTitle()         { return title; }
    public String getArtist()        { return artist; }
    public String getAlbum()         { return album; }
    public int getDurationSeconds()  { return durationSeconds; }
    
    public String getFormattedDuration() {
        return String.format("%d:%02d", durationSeconds / 60, durationSeconds % 60);
    }
    
    @Override
    public String toString() {
        return "\"" + title + "\" by " + artist + " (" + getFormattedDuration() + ")";
    }
}
```

### Playlist (Collection + Navigation)

```java
// ==================== PLAYLIST ====================
// 💬 "Playlist manages the ordered collection and index-based navigation.
//    Repeat/shuffle logic lives here because it's about traversal order."

class Playlist {
    private final String name;
    private final List<Song> songs;
    private int currentIndex;
    private boolean shuffleEnabled;
    private List<Integer> shuffleOrder;  // shuffled indices
    
    public Playlist(String name) {
        this.name = name;
        this.songs = new ArrayList<>();
        this.currentIndex = 0;
        this.shuffleEnabled = false;
    }
    
    public void addSong(Song song) { songs.add(song); }
    
    public void removeSong(String songId) {
        songs.removeIf(s -> s.getId().equals(songId));
        if (currentIndex >= songs.size()) currentIndex = Math.max(0, songs.size() - 1);
    }
    
    public Song getCurrent() {
        if (songs.isEmpty()) return null;
        int idx = shuffleEnabled ? shuffleOrder.get(currentIndex) : currentIndex;
        return songs.get(idx);
    }
    
    /**
     * Move to next song. Returns true if moved, false if at end.
     * Does NOT handle repeat logic — caller (MusicPlayer) decides.
     */
    public boolean moveNext() {
        if (currentIndex < songs.size() - 1) {
            currentIndex++;
            return true;
        }
        return false;  // at end of playlist
    }
    
    /** Move to previous song. Returns true if moved, false if at beginning. */
    public boolean movePrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            return true;
        }
        return false;  // at beginning of playlist
    }
    
    /** Wrap to first song (for REPEAT_ALL). */
    public void wrapToBeginning() {
        currentIndex = 0;
    }
    
    /** Wrap to last song (for previous at start with REPEAT_ALL). */
    public void wrapToEnd() {
        currentIndex = songs.size() - 1;
    }
    
    public boolean isAtEnd()       { return currentIndex >= songs.size() - 1; }
    public boolean isAtBeginning() { return currentIndex <= 0; }
    public boolean isEmpty()       { return songs.isEmpty(); }
    public int size()              { return songs.size(); }
    public int getCurrentIndex()   { return currentIndex; }
    public String getName()        { return name; }
    
    /** Toggle shuffle — regenerate random order. */
    public void toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        if (shuffleEnabled) {
            shuffleOrder = new ArrayList<>();
            for (int i = 0; i < songs.size(); i++) shuffleOrder.add(i);
            Collections.shuffle(shuffleOrder);
            // Keep current song at position 0 so it doesn't change
            int currentActual = shuffleOrder.indexOf(currentIndex);
            Collections.swap(shuffleOrder, 0, currentActual);
            currentIndex = 0;
        } else {
            // Restore to actual index of current song
            currentIndex = shuffleOrder.get(currentIndex);
            shuffleOrder = null;
        }
    }
    
    public List<Song> getSongs() { return Collections.unmodifiableList(songs); }
}
```

### State Pattern: Player States

```java
// ==================== PLAYER STATE INTERFACE ====================
// 💬 "State Pattern because each state has fundamentally different behavior:
//    - STOPPED.play() starts from beginning
//    - PAUSED.play() resumes from position
//    - PLAYING.play() is a no-op
//    Using enum+switch would scatter 3×5=15 cases across the MusicPlayer class."

interface PlayerState {
    void play(MusicPlayer player);
    void pause(MusicPlayer player);
    void stop(MusicPlayer player);
    void next(MusicPlayer player);
    void previous(MusicPlayer player);
    String getName();
}

// ==================== STOPPED STATE ====================
class StoppedState implements PlayerState {
    
    @Override
    public void play(MusicPlayer player) {
        Song song = player.getPlaylist().getCurrent();
        if (song == null) {
            System.out.println("  ⚠️ No songs in playlist");
            return;
        }
        player.setCurrentPosition(0);  // start from beginning
        player.setState(new PlayingState());
        player.notifyTrackChange(song);
        player.notifyStateChange("STOPPED", "PLAYING");
        System.out.println("  ▶️ Playing: " + song);
    }
    
    @Override
    public void pause(MusicPlayer player) {
        System.out.println("  ⚠️ Cannot pause — player is stopped");
        // No-op: nothing to pause
    }
    
    @Override
    public void stop(MusicPlayer player) {
        System.out.println("  ⚠️ Already stopped");
        // No-op: already stopped
    }
    
    @Override
    public void next(MusicPlayer player) {
        // In STOPPED state, next() moves cursor but doesn't auto-play
        player.advanceToNext();
        Song song = player.getPlaylist().getCurrent();
        if (song != null) {
            player.notifyTrackChange(song);
            System.out.println("  ⏭️ Selected: " + song + " (still stopped)");
        }
    }
    
    @Override
    public void previous(MusicPlayer player) {
        player.advanceToPrevious();
        Song song = player.getPlaylist().getCurrent();
        if (song != null) {
            player.notifyTrackChange(song);
            System.out.println("  ⏮️ Selected: " + song + " (still stopped)");
        }
    }
    
    @Override
    public String getName() { return "STOPPED"; }
}

// ==================== PLAYING STATE ====================
class PlayingState implements PlayerState {
    
    @Override
    public void play(MusicPlayer player) {
        System.out.println("  ⚠️ Already playing");
        // No-op: already playing (or could restart track)
    }
    
    @Override
    public void pause(MusicPlayer player) {
        // Position is preserved (player.currentPosition stays as-is)
        player.setState(new PausedState());
        player.notifyStateChange("PLAYING", "PAUSED");
        System.out.println("  ⏸️ Paused at " + player.getFormattedPosition());
    }
    
    @Override
    public void stop(MusicPlayer player) {
        player.setCurrentPosition(0);  // RESET position
        player.setState(new StoppedState());
        player.notifyStateChange("PLAYING", "STOPPED");
        System.out.println("  ⏹️ Stopped. Position reset.");
    }
    
    @Override
    public void next(MusicPlayer player) {
        player.advanceToNext();
        Song song = player.getPlaylist().getCurrent();
        if (song != null) {
            player.setCurrentPosition(0);
            player.notifyTrackChange(song);
            System.out.println("  ⏭️ Now playing: " + song);
            // State stays PLAYING
        }
        // If advanceToNext triggered stop (end of playlist, repeat OFF), state changes inside
    }
    
    @Override
    public void previous(MusicPlayer player) {
        player.advanceToPrevious();
        Song song = player.getPlaylist().getCurrent();
        if (song != null) {
            player.setCurrentPosition(0);
            player.notifyTrackChange(song);
            System.out.println("  ⏮️ Now playing: " + song);
        }
    }
    
    @Override
    public String getName() { return "PLAYING"; }
}

// ==================== PAUSED STATE ====================
class PausedState implements PlayerState {
    
    @Override
    public void play(MusicPlayer player) {
        // RESUME from current position — the key difference from StoppedState!
        player.setState(new PlayingState());
        player.notifyStateChange("PAUSED", "PLAYING");
        System.out.println("  ▶️ Resumed at " + player.getFormattedPosition());
    }
    
    @Override
    public void pause(MusicPlayer player) {
        System.out.println("  ⚠️ Already paused");
        // No-op: already paused
    }
    
    @Override
    public void stop(MusicPlayer player) {
        player.setCurrentPosition(0);  // RESET position
        player.setState(new StoppedState());
        player.notifyStateChange("PAUSED", "STOPPED");
        System.out.println("  ⏹️ Stopped. Position reset.");
    }
    
    @Override
    public void next(MusicPlayer player) {
        player.advanceToNext();
        Song song = player.getPlaylist().getCurrent();
        if (song != null) {
            player.setCurrentPosition(0);
            player.setState(new PlayingState());  // auto-play on next
            player.notifyTrackChange(song);
            player.notifyStateChange("PAUSED", "PLAYING");
            System.out.println("  ⏭️ Now playing: " + song);
        }
    }
    
    @Override
    public void previous(MusicPlayer player) {
        player.advanceToPrevious();
        Song song = player.getPlaylist().getCurrent();
        if (song != null) {
            player.setCurrentPosition(0);
            player.setState(new PlayingState());  // auto-play on previous
            player.notifyTrackChange(song);
            player.notifyStateChange("PAUSED", "PLAYING");
            System.out.println("  ⏮️ Now playing: " + song);
        }
    }
    
    @Override
    public String getName() { return "PAUSED"; }
}
```

### MusicPlayer (Coordinator)

```java
// ==================== MUSIC PLAYER ====================
// 💬 "MusicPlayer is the public API and state context. It delegates to the current
//    state for all operations. Repeat mode logic lives here because it's a
//    cross-cutting concern that affects navigation, not individual state behavior."

class MusicPlayer {
    private Playlist playlist;
    private PlayerState state;
    private RepeatMode repeatMode;
    private int currentPositionSeconds;
    private final List<PlayerObserver> observers;
    
    public MusicPlayer() {
        this.state = new StoppedState();
        this.repeatMode = RepeatMode.OFF;
        this.currentPositionSeconds = 0;
        this.observers = new ArrayList<>();
    }
    
    // ===== PLAYBACK CONTROLS (delegate to state) =====
    
    public void play()     { state.play(this); }
    public void pause()    { state.pause(this); }
    public void stop()     { state.stop(this); }
    public void next()     { state.next(this); }
    public void previous() { state.previous(this); }
    
    // ===== REPEAT MODE =====
    
    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
        System.out.println("  🔁 Repeat mode: " + mode);
    }
    
    public void cycleRepeatMode() {
        switch (repeatMode) {
            case OFF:         repeatMode = RepeatMode.REPEAT_ALL; break;
            case REPEAT_ALL:  repeatMode = RepeatMode.REPEAT_ONE; break;
            case REPEAT_ONE:  repeatMode = RepeatMode.OFF; break;
        }
        System.out.println("  🔁 Repeat mode: " + repeatMode);
    }
    
    // ===== NAVIGATION LOGIC (called by states) =====
    
    /**
     * Advance to next song respecting repeat mode.
     * Called by state objects — they handle play/pause transitions.
     */
    void advanceToNext() {
        if (playlist == null || playlist.isEmpty()) return;
        
        boolean moved = playlist.moveNext();
        
        if (!moved) {
            // At end of playlist
            switch (repeatMode) {
                case REPEAT_ALL:
                    playlist.wrapToBeginning();
                    System.out.println("  🔁 Playlist restarted from beginning");
                    break;
                case REPEAT_ONE:
                    // Stay on current song, reset position
                    currentPositionSeconds = 0;
                    System.out.println("  🔂 Repeating: " + playlist.getCurrent());
                    break;
                case OFF:
                    // End of playlist — stop
                    currentPositionSeconds = 0;
                    state = new StoppedState();
                    notifyStateChange("PLAYING", "STOPPED");
                    System.out.println("  ⏹️ Playlist ended.");
                    break;
            }
        }
    }
    
    void advanceToPrevious() {
        if (playlist == null || playlist.isEmpty()) return;
        
        boolean moved = playlist.movePrevious();
        
        if (!moved) {
            // At beginning of playlist
            if (repeatMode == RepeatMode.REPEAT_ALL) {
                playlist.wrapToEnd();
                System.out.println("  🔁 Wrapped to last song");
            }
            // OFF and REPEAT_ONE: stay on first song
        }
    }
    
    /**
     * Called when a song finishes playing (position reaches duration).
     * Simulates the auto-advance behavior.
     */
    public void onSongComplete() {
        if (repeatMode == RepeatMode.REPEAT_ONE) {
            // Loop same song
            currentPositionSeconds = 0;
            notifyTrackChange(playlist.getCurrent());
            System.out.println("  🔂 Looping: " + playlist.getCurrent());
        } else {
            // Try to advance to next
            next();
        }
    }
    
    // ===== PLAYLIST MANAGEMENT =====
    
    public void loadPlaylist(Playlist playlist) {
        stop();
        this.playlist = playlist;
        System.out.println("  📋 Loaded playlist: \"" + playlist.getName() 
            + "\" (" + playlist.size() + " songs)");
    }
    
    // ===== OBSERVER PATTERN =====
    
    public void addObserver(PlayerObserver observer) { observers.add(observer); }
    public void removeObserver(PlayerObserver observer) { observers.remove(observer); }
    
    void notifyStateChange(String from, String to) {
        observers.forEach(o -> o.onStateChange(from, to));
    }
    
    void notifyTrackChange(Song song) {
        observers.forEach(o -> o.onTrackChange(song));
    }
    
    // ===== GETTERS / SETTERS =====
    
    public PlayerState getState()        { return state; }
    void setState(PlayerState state)     { this.state = state; }
    public Playlist getPlaylist()        { return playlist; }
    public RepeatMode getRepeatMode()    { return repeatMode; }
    public int getCurrentPosition()      { return currentPositionSeconds; }
    void setCurrentPosition(int pos)     { this.currentPositionSeconds = pos; }
    public Song getCurrentSong()         { return playlist != null ? playlist.getCurrent() : null; }
    public String getStateName()         { return state.getName(); }
    
    public String getFormattedPosition() {
        return String.format("%d:%02d", currentPositionSeconds / 60, currentPositionSeconds % 60);
    }
    
    public String getStatus() {
        Song song = getCurrentSong();
        return "[" + state.getName() + "] " 
            + (song != null ? song.toString() : "No song")
            + " | " + getFormattedPosition()
            + " | Repeat: " + repeatMode;
    }
}
```

### Observer Interface

```java
// ==================== OBSERVER ====================
interface PlayerObserver {
    void onStateChange(String fromState, String toState);
    void onTrackChange(Song newSong);
}

// UI Observer
class UIObserver implements PlayerObserver {
    @Override
    public void onStateChange(String from, String to) {
        System.out.println("    [UI] State: " + from + " → " + to);
    }
    
    @Override
    public void onTrackChange(Song song) {
        System.out.println("    [UI] Now playing: " + song.getTitle() + " - " + song.getArtist());
    }
}

// Scrobble Observer (Last.fm / listening history)
class ScrobbleObserver implements PlayerObserver {
    @Override
    public void onStateChange(String from, String to) {
        // Only log when a song starts playing
        if ("PLAYING".equals(to)) {
            System.out.println("    [Scrobble] Started playing — will scrobble at 50%");
        }
    }
    
    @Override
    public void onTrackChange(Song song) {
        System.out.println("    [Scrobble] Scrobbled: " + song.getTitle());
    }
}
```

### RepeatMode Enum

```java
enum RepeatMode {
    OFF("No Repeat"),
    REPEAT_ONE("Repeat Song 🔂"),
    REPEAT_ALL("Repeat Playlist 🔁");
    
    private final String displayName;
    
    RepeatMode(String displayName) { this.displayName = displayName; }
    
    public String getDisplayName() { return displayName; }
    
    @Override
    public String toString() { return displayName; }
}
```

### Demo

```java
public class MusicPlayerDemo {
    public static void main(String[] args) {
        MusicPlayer player = new MusicPlayer();
        player.addObserver(new UIObserver());
        
        // Create playlist
        Playlist playlist = new Playlist("Classic Rock");
        playlist.addSong(new Song("1", "Bohemian Rhapsody", "Queen", "A Night at the Opera", 354));
        playlist.addSong(new Song("2", "Hotel California", "Eagles", "Hotel California", 391));
        playlist.addSong(new Song("3", "Stairway to Heaven", "Led Zeppelin", "Led Zeppelin IV", 482));
        
        player.loadPlaylist(playlist);
        
        System.out.println("\n=== Basic Playback ===");
        player.play();                    // STOPPED → PLAYING "Bohemian Rhapsody"
        player.pause();                   // PLAYING → PAUSED at current position
        player.play();                    // PAUSED → PLAYING (resume!)
        player.stop();                    // PLAYING → STOPPED, position reset
        
        System.out.println("\n=== Navigation ===");
        player.play();                    // Play "Bohemian Rhapsody"
        player.next();                    // Skip to "Hotel California"
        player.next();                    // Skip to "Stairway to Heaven"
        player.next();                    // End of playlist → STOPPED (RepeatMode.OFF)
        
        System.out.println("\n=== Repeat All ===");
        player.setRepeatMode(RepeatMode.REPEAT_ALL);
        player.play();                    // Play "Stairway to Heaven" (last cursor position)
        player.next();                    // Wraps to "Bohemian Rhapsody"!
        
        System.out.println("\n=== Repeat One ===");
        player.setRepeatMode(RepeatMode.REPEAT_ONE);
        player.onSongComplete();          // Song ends → loops same song!
        player.next();                    // Manual skip still works
        
        System.out.println("\nFinal status: " + player.getStatus());
    }
}
```

---

## 6️⃣ Deep Dives

### Deep Dive 1: State Pattern vs Enum + Switch

| Approach | States × Operations | Where Logic Lives | Extensibility |
|----------|-------------------|-------------------|---------------|
| **Enum + Switch** | 3 states × 5 ops = 15 cases in one class | All in MusicPlayer | Add state = modify MusicPlayer |
| **State Pattern** | 3 classes × 5 methods = 15 methods spread across 3 classes | Each state handles its own | Add state = new class, zero changes |

**Why State Pattern here**: The key difference is in `play()`:
- `StoppedState.play()` → start from position 0
- `PausedState.play()` → resume from saved position
- `PlayingState.play()` → no-op

These are fundamentally different behaviors, not just different data values. State pattern makes each behavior explicit and encapsulated.

**When enum + switch would be fine**: If all states had the same behavior with just different boolean checks (e.g., a light switch: ON/OFF). For 3+ states with distinct logic, always prefer State pattern.

### Deep Dive 2: Repeat Mode Placement

**Design decision**: Where does repeat logic live?

| Option | Pros | Cons |
|--------|------|------|
| **In Playlist.moveNext()** | Playlist handles all navigation | Playlist needs to know about repeat modes (SRP violation) |
| **In PlayerState.next()** | States handle everything | Repeat logic duplicated across 3 states |
| **In MusicPlayer.advanceToNext()** ✅ | Centralized, states delegate | One extra indirection |

**We chose MusicPlayer** because repeat mode is a **cross-cutting concern** — it affects navigation regardless of which state we're in. States call `player.advanceToNext()` which handles repeat logic once.

### Deep Dive 3: Shuffle Implementation

```java
// Shuffle creates a permuted index array
// Fisher-Yates shuffle: O(N) time, O(N) space

void toggleShuffle() {
    shuffleEnabled = !shuffleEnabled;
    if (shuffleEnabled) {
        shuffleOrder = new ArrayList<>(IntStream.range(0, songs.size()).boxed().toList());
        Collections.shuffle(shuffleOrder);
        
        // KEY: Keep current song at position 0 so it doesn't change mid-play
        int currentActual = shuffleOrder.indexOf(currentIndex);
        Collections.swap(shuffleOrder, 0, currentActual);
        currentIndex = 0;  // current song is now at shuffled position 0
    }
}
```

**Spotify behavior**: When you enable shuffle, the currently playing song stays. The rest of the playlist is randomized. When you disable shuffle, you return to the original position. Our implementation matches this.

### Deep Dive 4: Observer Use Cases

| Observer | Trigger | Action |
|----------|---------|--------|
| **UIObserver** | State/track change | Update play/pause button, song title display |
| **ScrobbleObserver** | Track played >50% | Log to listening history (Last.fm) |
| **AnalyticsObserver** | Track started | Count plays, popular songs analytics |
| **QueueObserver** | Track ended | Auto-play next from "Up Next" queue |
| **LyricsObserver** | Track changed | Fetch and display lyrics |
| **EqualizerObserver** | Track changed | Apply genre-specific EQ preset |

**Why Observer?** MusicPlayer doesn't know or care what happens when a track changes. Adding Spotify Wrapped analytics = one new class, zero changes to MusicPlayer.

### Deep Dive 5: Queue System (P1 Extension)

```java
class PlaybackQueue {
    private final Deque<Song> userQueue;    // "Play Next" songs — inserted by user
    private final Playlist basePlaylist;     // The loaded playlist
    
    public Song getNext() {
        if (!userQueue.isEmpty()) {
            return userQueue.pollFirst();  // User queue takes priority
        }
        basePlaylist.moveNext();
        return basePlaylist.getCurrent();
    }
    
    public void playNext(Song song) {
        userQueue.addFirst(song);  // Goes to front of queue
    }
    
    public void addToQueue(Song song) {
        userQueue.addLast(song);   // Goes to end of queue
    }
}
```

**Spotify queue behavior**:
1. "Play Next" → inserted at front of user queue
2. "Add to Queue" → appended to end of user queue
3. User queue drains first, then playlist continues
4. Pressing Previous goes back to playlist, not queue

### Deep Dive 6: Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Empty playlist + play() | Print warning, stay STOPPED |
| next() at end, repeat OFF | Stop playback |
| next() at end, repeat ALL | Wrap to first song |
| previous() at start, repeat OFF | Stay on first song |
| previous() at start, repeat ALL | Wrap to last song |
| play() when already playing | No-op (or restart from beginning — configurable) |
| pause() when stopped | No-op with warning |
| Remove currently playing song | Advance to next song, or stop if last |
| Load new playlist while playing | Stop current, load new, reset to STOPPED |
| Song duration = 0 | Immediately trigger onSongComplete() |
| Single song playlist + repeat ALL | Loops the single song |
| Shuffle with 1 song | No-op (nothing to shuffle) |

### Deep Dive 7: Complexity Analysis

| Operation | Time | Space | Note |
|-----------|------|-------|------|
| play() | O(1) | O(1) | State delegation + notification |
| pause() | O(1) | O(1) | State change only |
| stop() | O(1) | O(1) | Reset position + state change |
| next() | O(1) | O(1) | Index increment + boundary check |
| previous() | O(1) | O(1) | Index decrement + boundary check |
| toggleShuffle() | O(N) | O(N) | Fisher-Yates shuffle of N songs |
| addSong() | O(1) | O(1) | Amortized ArrayList append |
| removeSong() | O(N) | O(1) | Scan for song to remove |
| Observer notification | O(K) | O(1) | K = number of observers |

### Deep Dive 8: Production Spotify Features

| Feature | Design | Pattern |
|---------|--------|---------|
| **Crossfade** | Start next song N seconds before current ends, blend volume | Observer on position update |
| **Gapless playback** | Pre-buffer next song while current plays | Strategy for buffer management |
| **Offline mode** | Cache songs locally with DRM encryption | Decorator: CachedSong wraps Song |
| **Collaborative playlist** | Multiple users edit same playlist in real-time | Observer + CRDT for conflict-free editing |
| **Radio/auto-play** | When playlist ends, generate recommendations | Strategy: RecommendationStrategy |
| **Podcast vs Music** | Different playback behavior (bookmarking, speed control) | State pattern already supports this — add new states |
| **Connect (device switching)** | Transfer playback between devices | Command pattern: serialize player state as command |

---

## 📋 Interview Checklist (L63)

- [ ] **State Pattern**: 3 player states (STOPPED, PLAYING, PAUSED) with encapsulated behavior per state
- [ ] **Key insight**: `StoppedState.play()` starts from 0, `PausedState.play()` resumes from position
- [ ] **State transitions**: Clear valid/invalid transitions, no-op for invalid ones
- [ ] **Repeat modes**: OFF (stop at end), REPEAT_ONE (loop song), REPEAT_ALL (loop playlist)
- [ ] **REPEAT_ONE**: Loops on auto-complete, but manual next/previous still skips
- [ ] **Navigation**: next/previous with boundary handling (wrap for REPEAT_ALL, stop for OFF)
- [ ] **Observer**: Decouple UI/analytics/scrobbling from player logic
- [ ] **Shuffle**: Fisher-Yates shuffle preserving current song at position 0
- [ ] **Separation of concerns**: Song (data), Playlist (collection), State (behavior), Player (coordinator)
- [ ] **Extensibility**: New state = new class, new observer = new class, zero changes to existing code

### Time Spent:
| Phase | Target |
|-------|--------|
| Understanding + Requirements | 3-5 min |
| Core Entities (Song, Playlist, State, Player) | 5 min |
| State Pattern Implementation (3 states) | 10 min |
| Repeat Mode + Navigation Logic | 5 min |
| Observer + Demo | 5 min |
| Deep Dives (shuffle, queue, edge cases) | 5 min |
| **Total** | **~35 min** |

### Design Pattern Summary

| Pattern | Where Used | Why |
|---------|-----------|-----|
| **State** | PlayerState interface + 3 states | play() behaves differently in STOPPED vs PAUSED — encapsulate per-state behavior |
| **Observer** | PlayerObserver for UI/scrobble/analytics | Decouple notification targets from player logic |
| **Strategy** | RepeatMode determines next-song behavior | Swappable traversal strategy without modifying player |
| **Singleton** | MusicPlayer (one active player instance) | Only one player can play at a time (optional) |

See `MusicPlayerSystem.java` for full implementation with playback, navigation, repeat, and shuffle demos.
