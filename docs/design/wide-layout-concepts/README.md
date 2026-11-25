# Wide-screen composition concepts — 2026-11-25

Status: Robin selected C on 2026-11-25 and authorised implementation for larger browser/iPad windows, with mobile unchanged. DESIGN records the approved width threshold and fallback. A and B remain alternative proposals; B's album grid is not approved. The original generated images remain illustrative rather than pixel-level specifications.

- **A — Library + player dock:** broad Library at left, bounded persistent player at right; the most direct reuse of the existing views. Original Library-first alternative. [Image](a-library-player-dock.png).
- **B — Player-led tablet:** player at left, album collection and track context at right; explores a more visual landscape-tablet composition. The album grid/context combination is an additional proposal. [Image](b-player-led-tablet.png).
- **C — Library, player and queue:** three panes for a particularly wide desktop; persistent Queue was approved with C for windows wide enough to fit it. [Image](c-library-player-queue.png).

All three remove the duplicate mini-player while a full player is visible. Keep the actual PlayerFace/TransportWheel and four outer controls authoritative. Generated cover art, symbols, typography, surface shading and dimensions are illustrative; the actual Graphite tokens/components govern any implementation. Concept images do not verify touch sizes, contrast, keyboard navigation, responsiveness or playback.

## Generation and prompt set

Generated with the built-in ImageGen tool, using Robin's supplied wide Library and Now Playing screenshots as reference inputs. Source images remain in the conversation; no credentials or personal audio were used. Each concept was a separate generation. B received one targeted refinement for the centre pause button, four satellite actions, active pill text and Profile glyph. Saved images are the selected final variants; originals remain in the tool's generated-images directory.

The prompt set specified:

**Shared:** high-fidelity flat front-facing Normal Bias UI concept; charcoal #202124, graphite #262626, recessed #171717 wells, #3d3d3d rims, white #f3f3f1, muted #b7b8bb, amber #eac23f; compact category pills/search; existing Roxy Music/Oasis examples; preserve the actual circular wheel with previous/pause/next and four separate info/queue/restart/shuffle controls. No favourites, repeat, volume, downloads, persistent playlists, extra transport or device perspective. Mobile unchanged; proposals only.

**A:** landscape desktop, roughly 66% Library and 34% permanent right-hand player. Show compact album identity header/Close/Queue actions and continuous circular-art track rows; bounded artwork, metadata well, progress and full wheel on the right. One shared toolbar, no mini-player footer. Label A — Library + player dock.

**B:** landscape 4:3 tablet, roughly 40% persistent left-hand player and 60% Library. Album category selected; two-column four-album cover grid, selected album context and three track rows. One shared toolbar and Profile. No duplicate mini-player. Label B — Player-led tablet. Refine only the control group to retain a distinct central hardware pause button and correct info/queue/restart/shuffle satellite actions; dark active pill with amber text, white Profile glyph and restrained timeline.

**C:** landscape wide desktop, roughly 45% Library, 32% full player and 23% Queue. Library has compact categories/search, rows and A–Z/# rail. Queue has count/current indication, existing Shuffle/Clear actions and upcoming rows, with no per-row removal or drag handles. Fine pane seams, shared toolbar and no duplicate mini-player. Label C — Library, player and queue.
