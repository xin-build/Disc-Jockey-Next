[English](README.md) | [中文](README_CN.md)

---

# Disc Jockey: Next

A Fabric mod that plays [Note Block Studio](https://www.stuffbydavid.com/mcnbs) (`.nbs`) songs in Minecraft by automatically interacting with in-game note blocks.

> Forked from [Disc Jockey](https://github.com/SemmieDev/Disc-Jockey) and extensively enhanced with new features, a redesigned UI, and numerous bug fixes.

---

## New Features (vs. Original Disc Jockey)

### Core Playback
- **4 Loop Modes** — No loop, List loop, Single loop, and **Shuffle loop** (randomizes next track each time)
- **Speed Control** — Adjust playback speed from 0.10x to 10.00x in 0.25x steps via on-screen buttons
- **Pause Resilience** — Auto-detects game pause/resume and prevents note explosion when unpausing
- **Stop After Current** — Loop mode 0 automatically stops after the song finishes

### Song Management
- **Favorites System** — Star/unstar songs, with a dedicated favorites view
- **Folder Navigation** — Organize songs into subdirectories; browse folders in the GUI with back/forward navigation
- **Three Sort Modes** — Sort by name, creation time, or last played, each with ascending/descending toggle
- **Real-time Search** — Filter songs as you type with whitespace-insensitive matching
- **Reload Songs** — Reload all songs from disk without restarting
- **Double-click to Play** — Double-click any song to start playback immediately
- **Drag & Drop** — Drop `.nbs` files onto the screen to import them
- **Open Music Folder** — One-click button to open the songs folder in your file explorer

### UI / UX
- **Progress Bar with Seek** — Click anywhere on the progress bar to jump to that position; displays `current / total` time
- **Jump-to-Time Input** — Type an exact timestamp (e.g. `1:30` or `90`) to jump precisely
- **Tooltips** — Hover over any button for a descriptive label
- **Required Blocks Overlay** — HUD panel at screen top-right showing which note block instruments the current song needs, how many are nearby, with color-coded rows (green = sufficient, red = missing) and block names
- **Custom Settings Screen** — Fully custom, no Cloth Config GUI dependency; scrollable, with direct value input, +/- buttons, save and reset-to-defaults buttons
- **Rounded Corners** — Subtle rounded corners on the blocks overlay and tooltip popups

### Quality of Life
- **Creative Mode Guard** — Shows a warning when trying to play in Creative mode (note blocks require Survival)
- **Automatic Blocks Overlay Close** — Optional auto-hide of the blocks panel when playback starts

---

## Full Feature List

| Category | Feature |
|----------|---------|
| Playback | Play `.nbs` files via note block interaction |
| Playback | Auto-tunes nearby note blocks to match the song |
| Playback | Client-side preview mode (no note block interaction) |
| Playback | Configurable speed (0.10x – 10.00x) |
| Playback | Progress bar with click-to-seek |
| Playback | Jump-to-time input (supports `MM:SS` format) |
| Playback | 4 loop modes: off, list, single, shuffle |
| Playback | Playlist queue with skip-to-next |
| Playback | Shuffle play — random order from current view |
| Library | Subdirectory support for organizing songs |
| Library | Favorites system with star toggle |
| Library | Sort by name / creation time / last played |
| Library | Real-time search filter |
| Library | Reload songs from disk |
| Library | Drag & drop file import |
| Library | Open music folder button |
| HUD | Required blocks panel with instrument names and counts |
| HUD | Color-coded sufficiency indicators |
| HUD | Configurable position and width |
| Settings | Custom scrollable config screen |
| Settings | Editable numeric values with +/- buttons |
| Settings | Save / Reset to defaults |
| Settings | Anti-cheat warning toggle |
| Settings | Async playback toggle |
| Settings | Omnidirectional sound toggle |
| Settings | Playback delay configuration |
| UI | Button tooltips |
| UI | Double-click to play |
| UI | Creative mode warning |

---

## Requirements

| Dependency       | Version          |
|------------------|------------------|
| Minecraft        | 26.2 (1.26.2)    |
| Fabric Loader    | >= 0.19.3        |
| Fabric API       | 0.152.2+26.2     |
| Java             | >= 25            |
| Cloth Config API | * (config serialization) |
| Mod Menu         | * (recommended)  |

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/), [Fabric API](https://modrinth.com/mod/fabric-api), and [Cloth Config API](https://modrinth.com/mod/cloth-config)
2. Download the latest `.jar` from [Releases](https://github.com/xin-build/Disc-Jockey-Next/releases)
3. Place it in `.minecraft/mods/`
4. Place `.nbs` songs in `.minecraft/config/disc_jockey/songs/` (subdirectories supported)
5. Launch the game — press `J` to open the menu

---

## How to Use

1. **Place note blocks** around you with the correct instruments (check the blocks overlay to see what's needed)
2. Press `J` to open the Disc Jockey screen
3. Select a song, choose loop mode and speed
4. Click **Play** — the mod auto-tunes nearby note blocks and starts playing
5. Use the **progress bar** to seek, **speed buttons** to adjust tempo, and the **jump input** for precise positioning

---

## Controls

| Key     | Action                     |
|---------|----------------------------|
| `J`     | Open song menu             |
| `Alt+D` | Toggle blocks overlay      |

---

## Configuration

Accessible via the wrench icon in the song screen or Mod Menu:

| Setting                        | Default | Description |
|--------------------------------|---------|-------------|
| Hide Warning                   | Off     | Suppress the anti-cheat message |
| Disable Async Playback         | Off     | Run playback on the main thread (fixes issues on some servers) |
| Omnidirectional Sounds         | On      | Note block sounds come from all directions (more pleasant) |
| Delay Playback Start           | 0.0s    | Wait after tuning before playing |
| HUD X / Y                      | 2, 2    | Blocks overlay position |
| HUD Width                      | 180     | Blocks overlay panel width (px) |
| Default Speed                  | 1.0     | Default playback speed |
| Close Overlay on Play          | On      | Automatically hide the blocks panel when playback starts |

---

## Credits

- **Original Disc Jockey**: [SemmieDev](https://github.com/SemmieDev/Disc-Jockey) & EnderKill98
- Powered by [Fabric](https://fabricmc.net/) & [Cloth Config](https://github.com/shedaniel/cloth-config)

---

## License

MIT — See [LICENSE](LICENSE)
