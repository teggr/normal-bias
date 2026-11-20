# Graphite — revised design proposal

2026-11-19 · Revision 2 · Visual direction approved · Based on origin/main `7086be0`.

Robin rejected the previous flat Quiet Hi-Fi interpretation. The attached screenshots clarify the intended direction: a dark old-school iPod-like player with tactile controls, paired with spacious dark lists. This revision supersedes the earlier proposal. Robin approved this revised visual direction on 2026-11-19; implementation targets the existing web client and desktop simulator. New feature proposals remain separate from styling approval.

[Revised mockup source](design/graphite-player.html) contains Click-wheel player, Spacious library and Desktop system views. The earlier `quiet-hi-fi.html` is retained only as a superseded review artifact. These HTML fragments use conversation-provided icons/carousel controls and are not application source.

## The defining visual features

The player should look like a physical music device. A charcoal body contains three distinct areas: a black artwork recess, an inset track-information strip, and a sculpted circular control wheel. Large album artwork provides most of the colour. The artwork remains square with gently rounded corners. The progress bar is thin and white, with small elapsed and remaining times below.

The wheel is dark graphite, with a slightly recessed outer ring and a raised central play/pause button. Previous and next sit on its left and right. White symbols contrast with the dark materials. Separate circular buttons sit around the wheel. Edge highlights, narrow black seams and restrained shadows communicate depth; the player does not use a large amber-filled play button. Controls depress on press without changing their layout.

The library follows the Daft Punk reference: dark continuous surfaces, generous vertical rhythm, small circular artwork, white track names, quieter artist labels, almost invisible separators and tiny amber details. Rows are not individual cards. A persistent compact player belongs to the same dark surface, with a fine amber progress line and a softly raised navigation capsule.

## Shared design tokens

| Role | Value / rule |
| --- | --- |
| Surround / artwork recess | `#141414` |
| Library surface | `#202124` |
| Player body | `#272727` |
| Raised control | Subtle `#2D2D2D` to `#252525` gradient |
| Recessed track strip | `#171717` |
| Primary text / player symbols | `#F3F3F1` |
| Secondary text | `#B7B8BB` |
| Fine amber detail | `#EAC23F` |
| Decorative seam | `#111111`, paired with a faint upper edge highlight |
| Focus | Separate visible 2-unit amber outline; do not rely on shadows |
| List row | 82 logical units by default; review range 72–96 |
| Artwork thumbnail | 40–42 logical units, circular in library |
| Spacing | 8 / 12 / 16 / 24 / 32 logical units |
| Type | System sans; compact 13–14 track title, 11–12 secondary in reference-scale mockups; scale for accessible production text |

Dark is the requested visual direction. No light-theme alternative is proposed in this revision. Text and hit targets scale independently: small visual icons retain at least 44 pt iOS / 48 dp Android hit regions in production. At larger accessibility text sizes, rows grow and metadata wraps. Wheel targets must not overlap; artwork shrinks before transport becomes inaccessible. Provide a vertically arranged transport fallback when text scaling or screen constraints make the wheel impractical.

## Mockup views

- **Click-wheel player:** the primary reference-matching surface, including artwork recess, inset metadata, white seek bar, wheel and surrounding physical buttons.
- **Spacious library:** airy dark rows, circular cover thumbnails, restrained amber outlines, persistent mini-player and raised navigation capsule.
- **Desktop system:** the same physical player alongside a wider library. At narrow widths the two areas stack; the physical identity remains visible.

The mockups use fictional metadata and original CSS-drawn record-sleeve artwork. This is illustrative artwork, not an implementation of album-art extraction. The API currently does not supply artwork. Preview interactions are silent local state changes.

## Behaviour and feature scope

Existing search, pagination, playback, seeking and queue semantics remain the baseline. Production library queries remain paginated at 25 tracks; the five-row preview is only a design specimen. Playing, paused, buffering, empty-library, no-results and error states retain the same physical surfaces. Failures show readable text and Retry track; do not replace the screen or hide recovery behind a menu. Seek is disabled until duration is available. Queue navigation is disabled at its boundaries. Preserve focus visibility and accessible action names; announce state changes without announcing elapsed seconds continuously.

The reference's favourite, repeat and volume positions are included as **proposed controls** to evaluate the intended appearance. Those capabilities do not exist in the present client and are not silently authorised implementation scope. Their mockup toggles only demonstrate selected states. For a styling-only increment, omit unsupported actions or map positions to existing queue/library controls after feedback. Do not ship dead buttons. No social Follow action, lyrics or artist biographies are added.

## Native platform treatment

The tactile player and spacious lists are the shared product identity on every client. Native styling applies to system fonts, safe areas, back behaviour, scrolling, keyboards, menus, dialogs, accessibility and media integration. Do not flatten the custom player into generic system controls. iOS uses its navigation/dismissal conventions; Android uses system Back and platform sheets. Desktop supports pointer and keyboard interaction while retaining the physical player.

Within the existing Java/Codename One stack, many controls are framework-drawn. Native conventions are not a promise of UIKit/Compose widgets. Web and desktop simulator exist today; mobile playback and CarPlay remain unverified. CarPlay would use system media templates, not a custom click wheel.

## Delivered styling increment — refined against the reference screenshots

Robin rejected the first implementation as janky and requested refinement against the supplied screenshots. The client now uses an edge-to-edge physical player rather than a rounded card inside tabs. Artwork is 70% of the player width; the metadata inset is compact; the timeline has a thin white rail with elapsed and remaining times. Three transport actions sit on the recessed wheel, with four separate moulded buttons around it. The header uses small back/information hardware controls. Normal playback hides redundant status prose while keeping loading/error messages and retry accessible.

The library has single-row title/artist layouts, circular sleeve thumbnails, subtle separators and one small amber queue action. Track text/artwork is the play target. Wide windows size the sidebar to fit their height; compact Now Playing hides the browsing tabs/header. Real focusable CN1 controls retain accessible action names, including the track title for play targets. Original abstract sleeve placeholders are consistent across the list and player; no cover-art extraction is claimed.

The reference's unsupported favourite/repeat/volume actions are replaced by existing queue/retry/library actions on the outer controls; no dead buttons are shipped. Following the 2026-11-20 trial feedback, (…) opens Queue directly, while track details remain on the header information button. The navigation capsule and mini-player progress strip remain design targets, not delivered features. Native physical-device safe areas, back conventions and accessibility-text settings still require device validation. The supplied commercial artwork is a reference, not bundled album art.

## Implementation sequence

1. Agree the player proportions, depth and list spacing from this revision.
2. Define scoped CN1 UIIDs for shell, artwork recess, metadata well, hardware button, wheel, list row and mini-player. Keep structural Container transparent. Use density-aware dimensions and native fonts. Check pinned 7.0.282 capabilities; browser CSS gradients/shadows in these mockups must not be pasted into CN1 CSS. Use supported vector borders or a small custom painter for the wheel if needed.
3. Adapt LibraryForm into reusable library/player components while preserving Java API access, queue policy and browser/simulator media adapters. Avoid API changes in the styling increment.
4. Verify actual web and simulator output at compact/wide widths, large text and long metadata. Check keyboard focus, empty/error/retry states, playback continuity, seeking and queue boundaries. Run the existing build/smoke checks and record the real client, including actual sound for playback flows.
5. Address artwork support, new controls and physical-device integration as explicit additional increments.

The native platform guidance from the original request remains in force. The corrected material language is tactile graphite, not flat charcoal-and-amber panels.

### Library material follow-up — 2026-11-20

The approved All songs/Albums/Artists selectors use the same layered graphite hardware edge as the player’s (…) button, stretched into labelled pills. Active-view text is `#EAC23F`; inactive labels are `#F3F3F1`. Persistent view selection is separate from focus/pressed state. The Library-only search well matches the metadata inset: `#171717`, a fine `#3D3D3D` rim and 2.5 mm corners, with room for the native editor and search/reset controls. Root view titles are not repeated above the rows; artist/album drill-down retains its context and back action.
