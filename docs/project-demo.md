# Five-minute project demonstration

Prepared for Robin on 2026-11-27. Seven slides provide approximately 2½ minutes of personal context, followed by approximately 2½ minutes in the actual app. The saved editable Spaces presentation is **Normal Bias — my music, my player**, Page ID `page_0a436e0c65e08191903ef154b3966816`. Each slide includes speaker notes and relevant source links. The deck can be exported as PowerPoint from its viewer.

The repository includes the current [offline slideshow](presentation/index.html) and seven PNG slide exports in `docs/presentation/`. After cloning, open `docs/presentation/index.html` in a browser. Use Previous/Next, arrow keys, Space, Home/End, or Fullscreen. Keep the folder together so the images resolve. These exports preserve the saved deck's appearance, including Robin's later Spotify/iBroadcast screenshot inserts and wording edits. They are flattened visual exports; editable text and original notes remain in Spaces. GitHub's file view shows HTML source; download/clone it to present locally. The guide below supplies the speaking outline and source references independently of Spaces access.

PowerPoint export from the connected deck currently returns a signed-in download URL rather than a local file. No PPTX binary is included in the repository. The offline HTML/PNG export is the reviewable repository artifact, with the editable deck retained in Spaces.

## Approved presentation art direction

Robin selected concept 02 (acid-yellow rock/gig poster) as the foundation on 2026-11-27, with concept 06's rough zine edges and concept 07's festival-scale typography. A strong nostalgia undertone includes tapes, CDs, Napster, Kazaa, early MP3 players and iPods. The revised deck uses Impact headlines, Arial Narrow supporting text, yellow torn paper, off-white and hot-orange spreads. The actual dark app screenshots appear against bright backgrounds. This is presentation styling, not a change to the application's Graphite design.

The seven-slide story, source notes and suggested five-minute split remain. Revised display headlines include “The collection survived”, “Just my music”, “Then came the limits” and “So I made my own”. Decorative assets live in `docs/presentation/`: `yellow-torn-paper.png` and `music-nostalgia-collage.png` were generated with the built-in image-generation tool. The collage is illustrative period hardware, not Robin's actual possessions or an archival photograph. `iphone-cover.png` is an unchanged copy of the actual gallery capture used for reliable deck embedding. The app screenshots are not generated or redesigned.

Generation briefs: a blank 16:9 acid-yellow photocopied-paper backdrop with subtle grain, black edge distress and a torn off-white bottom strip; and a transparent photographic cutout collage of a cassette marked MIX 01, scratched CD/jewel case, silver USB MP3 player, classic white iPod and wired earbuds, with torn white photocopy edges and masking tape. The original concept board remains a proposal exploration, superseded by the selected combination above.

## Story and timings

| Time | Slide | Talking point |
| --- | --- | --- |
| 0:00–0:10 | Normal Bias | A personal player for the music I already have. |
| 0:10–0:35 | My musical clock stopped around 2015 | I still listen, but rarely look for new music. Deezer's survey offers evidence for familiar listening, without establishing a universal cut-off age. |
| 0:35–0:55 | The collection stayed with me | CDs and the MP3 years, including earlier Napster/Kazaa memories. Approximately 13,000 songs and 120 GB. iTunes and Windows Media Player provide recognisable 2015 context. |
| 0:55–1:15 | Spotify never quite fitted | Personal preference: paying for access to songs I already have, with podcasts and audiobooks mixed into the experience, does not fit how I want to listen. |
| 1:15–1:40 | iBroadcast was a great fit | My uploaded collection across apps/web/CarPlay for free, then announced limits. At 120 GB, I would need Unlimited. |
| 1:40–2:10 | A reason to build it | Codex Pioneers supplied a reason to experiment and tokens. Codename One was the Java framework I wanted to try. A personal need made a useful project. |
| 2:10–2:30 | Let me show you | Introduce the actual player and hand over to the application. |

These are suggested speaking windows, not measured rehearsal results. The work initiative, token access, service experience and musical preferences come from Robin's supplied account, not independent research. The collection estimate supersedes the older 30 GB estimate and does not describe the size of the demonstration fixture.

## Live demonstration, 2:30–5:00

1. **2:30–3:00:** Start in a prepared Library, browse Albums and open a familiar album.
2. **3:00–3:45:** Queue the album, press Play, show Now Playing, pause/resume and seek. Leave a few seconds for actual sound.
3. **3:45–4:15:** Open Queue, show upcoming songs, then Next track.
4. **4:15–4:45:** Show an already prepared iPhone/iPad view or native simulator. If including CarPlay, explicitly introduce the desktop head-unit prototype.
5. **4:45–5:00:** Close on having personal music in a player tailored to the way Robin wants to listen, and a useful project for exploring Codex and Java across platforms.

Prepare the app before presenting. Use the existing [demo setup guide](demo.md) rather than building, configuring accounts or signing in during the five-minute slot. The earlier local walkthrough at `client/target/recordings/repo-demo/demo.mp4`, if still available, and committed screenshot gallery provide a fallback. Its earlier verification remains historical; preparing these slides did not recapture or reverify that video.

## Evidence and wording

- **Familiar music:** [Deezer, 11 June 2018](https://thebackstage-deezer.com/music/suffer-musical-paralysis/) reports a survey of 5,000 people across France, the UK, Germany, the USA and Brazil. More than 65% said they only listened to familiar tracks. It is a commercial self-report survey, not proof that everyone stops discovering music at a particular birthday.
- **The age-33 headline:** [Ajay Kalia's original 2015 analysis](https://skynetandebert.com/2015/04/22/music-was-better-back-then-when-do-we-stop-keeping-up-with-popular-music/) examines artist popularity in US Spotify listening. It does not measure an age when people stop listening to music or universally stop discovery. The deck uses Robin's personal 2015 date, rather than asserting that headline as a scientific rule.
- **Software around 2015:** [Apple's June 2015 announcement](https://www.apple.com/newsroom/2015/06/08Introducing-Apple-Music-All-The-Ways-You-Love-Music-All-in-One-Place-/) refers to iTunes and ripped-CD libraries. [Microsoft's November 2015 media-pack documentation](https://support.microsoft.com/en-gb/servicing/os/windows-10/2015/11/media-feature-pack-for-windows-10-n-and-windows-10-kn-editions-november-2015) documents Windows Media Player. These are period examples, not a researched market-share ranking. Napster and Kazaa belong to Robin's earlier history. Winamp is an optional earlier MP3-era nostalgia reference in the notes, rather than an assertion about Robin's own player in 2015.
- **iBroadcast:** [The 11 November 2026 announcement](https://news.ibroadcast.com/new-premium-plans-are-here) says existing free users' limits take effect on **10 January 2027**. Free remains available with 5 GB storage and 5 GB monthly streaming. Basic includes 25 GB of each for $1.99/month or $21.99/year. Unlimited costs $4.99/month or $54.99/year. All amounts are USD, checked on 2026-11-27. A 120 GB hosted collection exceeds both lower storage allowances, so Unlimited is the applicable plan in this comparison. Free is becoming limited, not disappearing. The [service page](https://www.ibroadcast.com/) lists web, mobile and CarPlay support.

## Current application claims

The presentation uses existing real browser and iPhone simulator captures, with editable foreground text. It does not create mock application screens. Web listening works, native iPhone/iPad builds have simulator evidence, and some physical iPhone playback has been user-confirmed. Full physical authenticated/background/interruption acceptance remains open. CarPlay is an experimental desktop head-unit prototype, with native entitlement/runtime and real-car acceptance unverified. Android is a Codename One framework target, not a verified Normal Bias release. Deployment and backup remain unfinished. See the [README](../README.md), [project brief](project-brief.md) and dated [verification record](verification.md).

No application UI, API, media, authentication, retained service or device changed for this presentation. No new application recording is required for this documentation/presentation-only task.
