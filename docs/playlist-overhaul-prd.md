# Playlist Overhaul — Product Requirements Document

**Product:** FlamingoSank (Flamingo Music Player) — Android, local-library music player
**Author:** Product (drafted via guided PRD session)
**Status:** Draft v1
**Last updated:** 2026-05-17

---

## 1. Introduction / Overview

The current playlist experience in FlamingoSank is functional but visually and interactionally inconsistent with the rest of the app — most notably with the NowPlaying screen, which sets the design bar. This overhaul brings the playlist detail page in line with that bar while adding three capabilities the app currently lacks: **(a)** a pull-to-reveal in-playlist search, **(b)** a comprehensive Edit Playlist screen (rename, cover art, description, reorder, multi-select remove), and **(c)** first-class pin support with manual ordering.

This work is contained to the playlist detail page, the Library playlist list, and one new full-screen modal (Edit Playlist). It does not alter the Home page, NowPlaying, or any other navigation surface.

---

## 2. Goals / Objectives

### Primary goals
1. **Visual cohesion.** The playlist detail page's overflow menu visually and behaviorally matches the NowPlaying overflow menu (full-width bottom sheet with header, red leading icons on dark background).
2. **Fast in-playlist search.** Users can search within a playlist by any human-readable text metadata via a pull-down gesture, with results updating live.
3. **Full-featured editing.** Users can rename, re-cover, describe, reorder, and bulk-remove songs from a playlist in a single modal — including allowing duplicate songs.
4. **Pinning with manual order.** Users can pin any number of playlists to the top of the Library playlist list and reorder pins via long-press.
5. **No regressions.** Existing playlists migrate transparently to the new data model on first launch; no user action required.

### Non-goals (this release)
- Collaborative playlists, sharing to remote, cloud sync, downloads — these were Apple Music artifacts in the reference screenshots and are not applicable to a local-only library.
- Auto-favoriting, "Suggest Less," "Report a Concern" — not applicable to local library.
- Smart/dynamic playlists.
- Search across the entire library from this entry point (this feature scopes search to the currently-open playlist only).
- Analytics instrumentation.

### Success criteria
**Qualitative:** The redesigned playlist surface feels as polished as Apple Music's equivalent screens. Specifically:
- The 3-dot menu sheet is visually indistinguishable in structure from the NowPlaying sheet.
- Pull-to-reveal search responds smoothly with the existing `overscroll_core` rubber-band physics.
- Edit Playlist modal transitions in/out without jank on supported devices (armeabi-v7a, arm64-v8a, minSdk 23).
- Pin reorder is intuitive on first try (validated via informal usability check).

---

## 3. Target Audience / User Personas

### Persona A — "Curator Quinn" (primary)
A user with 500–5000 local tracks who actively builds and refines playlists. Reorders songs frequently, cares about playlist aesthetics (cover art, description), pins 3–10 playlists they listen to most.

### Persona B — "Casual Cam" (secondary)
A user with a handful of playlists who mostly just plays them. Will benefit from the polish but isn't the primary driver of editing/search features. Pull-to-reveal search must remain discoverable enough that Cam can find it; the 3-dot menu redesign should not break Cam's muscle memory for the existing actions.

### Persona C — "Power Pat" (tertiary)
A user with very large playlists (1000+ songs). The search and reorder flows must remain performant for them; this is the persona that justifies the debounced/async search architecture and the proportional fuzzy-match threshold.

---

## 4. User Stories / Use Cases

### Search
- **US-S1:** As Quinn, I want to swipe down at the top of a playlist to reveal a search bar so I can find a song without scrolling.
- **US-S2:** As Quinn, I want search to tolerate typos so I don't have to retype a query when I misspell an artist name.
- **US-S3:** As Quinn, I want search to match any metadata field (title, artist, album, album artist, genre, year, composer, comment) so I can find songs by partial info.
- **US-S4:** As Quinn, I want the search bar to stay open until I press the back button so I can scroll results without losing the bar.

### 3-dot menu
- **US-M1:** As Cam, I want the playlist's overflow menu to look and feel like the NowPlaying menu so the app feels consistent.
- **US-M2:** As Quinn, I want quick access to Sort By, Edit, Pin, Add to Playlist, Play Next, and Delete from one menu.

### Edit Playlist
- **US-E1:** As Quinn, I want to rename a playlist inline.
- **US-E2:** As Quinn, I want to change a playlist's cover art via a custom photo OR an auto-generated 2x2 collage of the playlist's first 4 unique album arts.
- **US-E3:** As Quinn, I want to add a short description (≤200 chars) to a playlist.
- **US-E4:** As Quinn, I want to reorder songs via drag handles.
- **US-E5:** As Quinn, I want to bulk-remove songs via checkboxes.
- **US-E6:** As Quinn, I want my edits to autosave on exit so I never lose work.
- **US-E7:** As Quinn, I want to add the same song multiple times to a playlist (e.g., to repeat an intro track).

### Pin
- **US-P1:** As Quinn, I want to pin playlists so my most-used ones float to the top of the Library list.
- **US-P2:** As Quinn, I want no cap on how many playlists I can pin.
- **US-P3:** As Quinn, I want to long-press a pinned playlist to enter reorder mode and drag pins into my preferred order.

### Delete
- **US-D1:** As Cam, I want deleting a playlist to be reversible via an undo snackbar so an accidental tap doesn't lose data permanently.

---

## 5. Functional Requirements

### 5.1 In-playlist Search

| # | Requirement |
|---|---|
| FR-S-01 | A search bar is **hidden by default** on the playlist detail page. |
| FR-S-02 | The bar is revealed by an overscroll-down gesture at the top of the song list. Implementation reuses the existing `overscroll_core` library (rubber-band physics). |
| FR-S-03 | Once revealed, the bar **persists** until the user presses the system back button or back gesture. Scrolling does not auto-hide it. |
| FR-S-04 | An "X" icon inside the search field clears the typed text but keeps the bar visible. |
| FR-S-05 | Search matches against all human-readable text metadata in each song's tag: title, artists, album, album artist, genre, year, composer, comment. Non-text metadata (bitrate, sample rate, etc.) is excluded. |
| FR-S-06 | Matching is **fuzzy** (Levenshtein edit distance) with a threshold proportional to query length: ≤1 typo for queries of 1–4 chars, ≤2 for 5–8 chars, ≤3 for 9+ chars. |
| FR-S-07 | Results are ranked by relevance (best match first). Relevance ranking **overrides the current Sort By choice** while a query is active. |
| FR-S-08 | Search execution is **debounced 150 ms** and runs on a background dispatcher (`Dispatchers.Default`). Results stream back to the UI via a `Flow`/`StateFlow`. |
| FR-S-09 | When a query yields zero matches, the list area is rendered empty (no message, no illustration). |
| FR-S-10 | The search query is **not persisted** across navigation away from the playlist detail page. Reopening the playlist starts with a hidden, empty search bar. |

### 5.2 Overflow (3-dot) Menu

| # | Requirement |
|---|---|
| FR-M-01 | Tapping the 3-dot icon on the playlist detail page opens a **full-width bottom sheet** that visually matches the NowPlaying overflow menu (reference: screenshot 1). |
| FR-M-02 | The sheet header shows the playlist's cover thumbnail, name, and (if present) description as a subtitle. |
| FR-M-03 | Menu rows use red leading icons on a dark background, with row labels in white. The destructive "Delete Playlist" row uses red label text. |
| FR-M-04 | The menu contains exactly these rows, in this order: **Sort By**, **Edit Playlist**, **Pin Playlist** / **Unpin Playlist** (label toggles based on current state), **Add to a Playlist…**, **Play Next**, **Delete Playlist**. |
| FR-M-05 | **Sort By** opens the existing sort options surface. Sorting is **view-only** — it does not modify the playlist's saved order. |
| FR-M-06 | **Edit Playlist** opens the full-screen Edit Playlist modal (§5.3). |
| FR-M-07 | **Pin Playlist / Unpin Playlist** toggles the playlist's `isPinned` state and assigns/clears its `pinOrder` (§5.4). The bottom sheet closes after the action. |
| FR-M-08 | **Add to a Playlist…** opens the existing playlist picker. Selecting a target playlist appends **all songs from the current playlist** to the target, preserving current playlist order. Duplicates in the target are permitted (see §5.5 data model). |
| FR-M-09 | **Play Next** inserts all songs from the current playlist into the playback queue immediately after the now-playing track, in current playlist order. |
| FR-M-10 | **Delete Playlist** deletes the playlist immediately and dismisses both the bottom sheet and the playlist detail page, returning to the Library. An **undo snackbar** appears in the Library for 5 seconds. Tapping "Undo" restores the playlist (name, description, cover, song list, pin state) exactly as it was. After the snackbar disappears, deletion is permanent. |

### 5.3 Edit Playlist Modal

| # | Requirement |
|---|---|
| FR-E-01 | Edit Playlist is a **full-screen modal** that slides up from the bottom (reference: screenshot 3). |
| FR-E-02 | The modal has an **X** (cancel/close) in the top-left and a **✓** (done) in the top-right. Both perform the **same action**: autosave all pending edits and close the modal. The ✓ is retained as a visual affirmative cue despite functional equivalence. |
| FR-E-03 | The top of the modal shows a **horizontal carousel** of cover options. The first slot is a camera/gallery picker (custom photo). Subsequent slots show auto-generated covers (see FR-E-05). The currently selected cover is visually highlighted; a row of dots below indicates carousel position. |
| FR-E-04 | The user may pick a custom image via the device's photo picker (Android Photo Picker API where available, fallback to `ACTION_OPEN_DOCUMENT`). |
| FR-E-05 | One auto-generated cover is always available: a **2x2 grid of the first 4 unique album arts** from the playlist, in current order. If the playlist contains fewer than 4 unique album arts, fall back to: 3 → 1x3 strip; 2 → 1x2 split; 1 → single album art filling the frame; 0 → default star/placeholder art. |
| FR-E-06 | Below the carousel: an editable **playlist name** field (single-line, max 100 chars). |
| FR-E-07 | Below the name: an editable **description** field (multi-line, optional, max **200 chars**, plain text). Empty by default. |
| FR-E-08 | Below the description: a list of every song in the playlist. Each row shows: a **checkbox** (leading), album art thumbnail, title, artist subtitle, and a **drag handle** (trailing). |
| FR-E-09 | Tapping a checkbox stages that song for removal. Multiple songs may be staged simultaneously. Staged songs visually remain in the list until the modal closes (no immediate removal animation while editing). |
| FR-E-10 | Dragging a row's handle reorders the song within the list. Reorder is immediate and reflected in the list visually. |
| FR-E-11 | On modal close (either X or ✓), the following are persisted atomically: name, description, cover art selection, new song order, and removal of all staged songs. |
| FR-E-12 | The playlist data model is updated so that **the same song ID may appear multiple times** in a playlist. Each occurrence is identified by a unique stable row ID so reorder/remove operations target the correct instance. |
| FR-E-13 | Adding songs to a playlist (via existing "Add to playlist" flows on individual songs) must permit duplicates — no implicit deduplication. |

### 5.4 Pinning

| # | Requirement |
|---|---|
| FR-P-01 | A playlist has a boolean `isPinned` and an integer `pinOrder` (only meaningful when pinned). Both persist in MMKV via the existing `PLAY_LIST` namespace. |
| FR-P-02 | The Library's playlist list renders pinned playlists in a contiguous block **at the top**, above all unpinned playlists. There is **no cap** on the number of pinned playlists. |
| FR-P-03 | Within the pinned block, playlists are ordered by ascending `pinOrder`. |
| FR-P-04 | Below the pinned block, unpinned playlists are ordered by the current Library sort preference (unchanged from today). |
| FR-P-05 | Pin/Unpin is triggered **only from the 3-dot menu** on the playlist detail page (no list-row swipe gesture, no long-press shortcut for toggling pin state). |
| FR-P-06 | When a playlist is newly pinned, its `pinOrder` is set to `max(existing pinOrder) + 1` so it appears at the bottom of the pinned block. |
| FR-P-07 | When a playlist is unpinned, its `pinOrder` is cleared. Remaining pinned playlists are **not** renumbered; gaps in `pinOrder` are tolerated. |
| FR-P-08 | **Long-pressing any pinned playlist row** in the Library enters "pin reorder mode": drag handles appear on all pinned rows; unpinned rows are visually dimmed and non-interactive. Tapping outside the pinned block, pressing back, or tapping a "Done" affordance exits reorder mode and commits the new `pinOrder` values. |

### 5.5 Data Model & Migration

| # | Requirement |
|---|---|
| FR-D-01 | The playlist contents model changes from `List<SongId>` (or equivalent) to `List<PlaylistEntry>` where `PlaylistEntry = { rowId: String (UUID), songId: SongId }`. |
| FR-D-02 | New persisted fields on the playlist object: `description: String?` (default null), `coverArtSource: CoverArtSource` (sealed: `Default` / `AutoCollage` / `CustomImage(uri)`), `isPinned: Boolean` (default false), `pinOrder: Int?` (default null). |
| FR-D-03 | On the first launch after upgrading to this release, all existing playlists are migrated transparently: each existing song reference is wrapped into a `PlaylistEntry` with a freshly minted UUID `rowId`. Order is preserved. Migration runs in `YosBasicApplication.onCreate` after MMKV/Gson init and before any playlist UI mounts. |
| FR-D-04 | Migration writes a sentinel key to MMKV (`playlist_model_v2_migrated = true`) so it runs at most once. |
| FR-D-05 | All new data classes in `yos.music.player.data.libraries.**` are added to the existing ProGuard `-keep` rule and to `stability_config.conf` if they are surfaced through Compose state. |

### 5.6 Cross-cutting

| # | Requirement |
|---|---|
| FR-X-01 | All string-bearing UI must have entries in `values/`, `values-ja/`, `values-zh-rCN/`, and `values-zh-rTW/`. |
| FR-X-02 | Every interactive icon (close, save, camera, drag handle, checkbox, search clear) has a `contentDescription`. |
| FR-X-03 | State-changing actions (pin, unpin, delete, undo delete, save edits) announce their outcome via `Modifier.semantics` live regions for TalkBack users. |

---

## 6. Non-Functional Requirements

### 6.1 Performance
- **NF-P-01** Search filtering for playlists ≤ 5000 songs must complete within 50 ms on arm64-v8a mid-tier devices (post-debounce), measured wall-clock from query change to result emission.
- **NF-P-02** Edit Playlist modal open-to-interactive time must be ≤ 250 ms on the same hardware class.
- **NF-P-03** Pin reorder drag must sustain ≥ 55 fps during gesture.
- **NF-P-04** Auto-collage cover generation must run on a background dispatcher and not block the main thread; placeholder shown while computing.

### 6.2 Reliability
- **NF-R-01** Autosave on modal close must be atomic: either all edits persist or none do (no half-applied state on crash mid-write).
- **NF-R-02** Migration (FR-D-03) must be idempotent and resilient to partial completion (a crash mid-migration leaves the data in a state where re-running completes safely).
- **NF-R-03** The undo snackbar's restore action must restore the deleted playlist byte-identical to its pre-delete state (including `pinOrder`).

### 6.3 Accessibility
- **NF-A-01** All interactive elements provide content descriptions in all four locales (FR-X-01).
- **NF-A-02** Touch targets meet Android's 48dp minimum.
- **NF-A-03** Color is not the sole signal of state (e.g., pin state, destructive actions also use icon/label cues).

### 6.4 Compatibility
- **NF-C-01** All features function on minSdk 23 through targetSdk 34.
- **NF-C-02** All features function on both supported ABIs (armeabi-v7a, arm64-v8a).
- **NF-C-03** No new third-party dependencies are introduced; fuzzy matching is implemented in-house.

### 6.5 Motion / Polish
- **NF-M-01** Search reveal uses the existing `overscroll_core` rubber-band physics — no custom motion code.
- **NF-M-02** The Edit Playlist modal and the overflow bottom sheet use Material 3 default enter/exit transitions; no custom spring physics.
- **NF-M-03** Pin/unpin in the Library list animates the row's position change with default `AnimatedItemPlacement` semantics.

---

## 7. Design Considerations / Mockups

### Reference screenshots (supplied)
1. **3-dot menu sheet** (Apple Music reference) — informs FR-M-01 through FR-M-04.
2. **Playlist detail page** (with red star "Favourite Songs" cover) — informs the cover-art treatment and the playlist header layout.
3. **Edit Playlist modal** — informs FR-E-01 through FR-E-10.

### Visual sources of truth in-repo
- Match the NowPlaying overflow menu's existing implementation in `app/src/main/java/yos/music/player/ui/pages/NowPlaying*.kt` for the bottom-sheet header + row styling.
- Reuse `overscroll_core`'s `Modifier.overScrollVertical` for the search reveal trigger.
- Reuse the existing playlist picker (referenced as "we'll reuse it" in the brain dump) for the **Add to a Playlist…** target selection.

### Open visual questions
- Exact red shade for destructive row label — defer to the existing app color tokens.
- Whether the pin indicator in the Library list is a leading icon, a trailing badge, or a row background tint — to be decided during implementation against the existing Library list row layout.

---

## 8. Success Metrics

This release is evaluated **qualitatively**. No analytics events are added.

| Signal | Pass criterion |
|---|---|
| Visual cohesion | A side-by-side comparison of NowPlaying overflow and Playlist overflow shows matching structure, spacing, color, and motion. |
| Gesture polish | Pull-to-reveal search on the playlist page feels physically consistent with other overscroll moments in the app. |
| Edit fluidity | A 10-song reorder + 3-song bulk-remove + rename + cover change completes in under 30 seconds for a familiar user. |
| Pin discoverability | A first-time user, told only "pin some playlists you like to the top," can complete the task without external help in under 60 seconds. |
| No crashes/data loss | Across 50 manual run-throughs of the edit + delete + undo flow, no playlists are corrupted or lost. |

---

## 9. Open Questions / Future Considerations

### Open questions
1. Where exactly does the pin reorder "Done" affordance live? Top app bar action, or implicit on tap-outside? (FR-P-08 currently allows both — implementation may pick one.)
2. Should the auto-collage update reactively when songs are added/removed/reordered, or is it snapshotted at cover-selection time?
   - **Working assumption:** snapshotted at selection time (avoids surprise cover changes). Validate during implementation review.
3. The fuzzy-match threshold values (1/2/3 typos at 4/8/9+ chars) are a starting point; tune during dogfooding.
4. Does the Edit Playlist modal need to support **adding** new songs (in addition to removing/reordering)? Brain dump did not mention it; current scope is remove + reorder only. Adding songs continues to happen via the existing per-song "Add to playlist" flows on the song's own overflow menu.

### Future considerations (out of scope)
- Smart playlists / dynamic rules.
- Shared / collaborative playlists.
- Library-wide search reusing the same fuzzy-match engine.
- Playlist folders / nesting.
- Cloud backup of pin state and playlist metadata.
- Reactive (live) auto-collage covers.
- Search bar revealable via icon as well as swipe (added if discoverability proves to be a problem in dogfooding).

---
