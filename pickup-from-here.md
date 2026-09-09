# Pickup from here

Notes for whichever session (on whichever machine) picks this project back
up next. Local Claude memory doesn't travel between computers, so anything
that should survive a machine switch belongs here instead.

## Backup and restore, and the main-thread parse fix (2026-09-09)

On branch `claude/library-backup` (commit `d150328`) — **not merged and not
pushed** as of this writing.

**Backup is a snapshot; restore replaces.** `LibraryBackup` writes the whole
library to one `.zip` placed through the system picker, so it can land
straight in Dropbox or Drive when those apps are installed. Restoring makes
the library match the archive — books it does not contain are removed. The
single exception is reading position: a book present in both keeps whichever
point is further along, because that is the only thing in the library that
cannot be recovered from anywhere else. Zip layout, which `LibraryManifest`
also defines for the undo snapshot:

```
library.json          format, createdAt, every row
books/<id>.txt        extracted text
books/<id>.cover      cover thumbnail
books/<id>.pdf        preserved original
books/<id>_epub/...   preserved original, as the unzipped tree it is stored as
```

**Undo.** Before a restore writes anything the current rows go to
`<filesDir>/undo/library.json`, and the files of books about to be removed
are **moved** (not copied) into `<filesDir>/undo/files/` — so the safety net
costs no extra space, it only defers the deletion. Undo sits next to "Done"
after a restore and, because the stash outlives that message, as "Undo last
restore" in the dialog until the next restore replaces it. One level, no redo.

**Auto Backup rules** — `res/xml/backup_rules.xml` (API 26–30) and
`data_extraction_rules.xml` (31+). `allowBackup` was already true, but Auto
Backup has a per-app quota that a single preserved original can exceed, and
exceeding it fails the *entire* backup silently — so it was protecting
nothing. Cloud backup now carries only the Room database; device-to-device
transfer has no such quota and still takes everything. Worth knowing: this
also makes the README's and the Play listing's "your books never leave the
device" substantially true for cloud backup, where before it was not.

**A missing text file no longer looks like a bug.** `getBookText` answers
null rather than `""`, and both reading screens show `MissingText.kt` instead
of a real-looking book with zero words and a dead slider. Those same paths
also used to bail out without clearing `loading` when the row itself was
gone, spinning a progress indicator forever.

**A user-chosen library folder was built and then removed — don't rebuild it
without asking.** It mirrored the library into a SAF tree so a sync tool
could carry it between devices, and it worked, but it needs merge semantics,
deletion tombstones and a background push; the call was that a backup is a
snapshot and that is enough. Two facts from it worth keeping if it ever comes
back: the Room database **cannot** live in a tree URI (SQLite needs a real
filesystem path), and document providers rename any file whose extension does
not match the MIME type it was created with, so that layout has to be flat
with matching extensions.

### Found while reviewing the whole app, not fixed

- **No tests at all.** `RsvpEngine.tokenize/paragraphs/orpIndex/wordDelayMs`,
  `EpubParser.normalizePath` and `SpeechController.chunkText` are pure
  functions with no Android dependencies — plain JVM tests, no emulator.
  `paragraphs()`'s invariant (same word order and count as `tokenize()`,
  which is what makes Browse's tap-to-jump indices valid) is asserted only in
  a comment.
- `ReaderViewModel.speakFromIdx`'s `onRangeStart` walks `offs` from index 0
  on every TTS word boundary. It is sorted; it wants a binary search. Late in
  a 130k-word book that is ~130k iterations several times a second.
- `ReaderScreen`'s speed slider calls `setWpm` on every drag pixel; in audio
  mode each call re-substrings the remaining book and restarts TTS.
  `onValueChangeFinished` is the fix.
- `LibraryScreen` decodes cover JPEGs with `BitmapFactory.decodeFile` during
  composition, on the main thread, for every visible card.
- `CoverPreviewDialog` calls `onDismiss()` from the composition body when a
  decode returns null — state mutation during composition.
- Deleting a book is one unguarded tap: no confirmation, no undo, and it
  takes the text, cover, original and progress with it.
- `AddBookScreen`'s save has no error path — an IOException out of
  `repo.addBook` escapes `rememberCoroutineScope` and crashes instead of
  using the error card the screen already has.
- `TextExtractor.extractEpub` reads every zip entry into memory with no cap,
  while `OriginalStore.stageEpub` caps both entry count and uncompressed size
  for the same untrusted file.
- `versionCode` is still hardcoded to 1 and CI does not check it, so a second
  tagged release would be rejected by Play. The signing config already fails
  fast in CI for exactly this class of problem.

## Release infrastructure and the premium unlock (2026-09-08)

[PR #9](https://github.com/loko087/LeerRapidon/pull/9) and
[PR #10](https://github.com/loko087/LeerRapidon/pull/10), both **merged to
`main`**. The app is now buildable as a shippable, signed artifact, which it
previously was not, and has a paywall on Play.

**The app is named Leer Rapidon.** The launcher icon always said so while
`app_name` said "Rapid Reader"; user-facing text is now consistent. The
package name `com.rapidreader.app` is deliberately unchanged — it is
permanent once published and never shown to users — as are internal Kotlin
symbols (`RapidReaderTheme`, `Theme.RapidReader`). Don't "fix" those.

**Two product flavours, and new code has to pick a source set.**
`app/src/play` links Play Billing and gates audio mode and the
original-form reader behind a one-time USD 0.99 `premium_unlock`;
`app/src/github` links no billing code at all and unlocks everything. Shared
code lives in `app/src/main` behind a `PremiumSource` interface, and each
flavour supplies its own `PremiumProvider`. This is a flavour rather than a
runtime flag because Play Billing only serves apps the Play Store installed —
a sideloaded APK cannot use it, and the GitHub build deliberately targets
people who would rather not use the Store. Build with `assembleGithubRelease`
/ `bundlePlayRelease`; plain `assembleRelease` no longer exists.

**Gotchas worth not rediscovering:**
- **Billing uses the non-ktx artifact on purpose.** `billing-ktx:9.1.0`
  ships Kotlin 2.3 metadata and this project is on Kotlin 1.9.24, so KSP
  fails outright. Nothing here uses the coroutine extensions. If Kotlin is
  ever upgraded to 2.x (which also means moving the Compose compiler to its
  own Gradle plugin), `-ktx` becomes available again.
- **R8 is on for release builds.** It can only realistically break the
  reflective paths, so a green compile proves nothing: after any dependency
  or keep-rule change, install a signed release build and import a PDF with
  a text layer, a scanned PDF (ML Kit OCR), and an EPUB. `app/proguard-rules.pro`
  explains why the `com.gemalto.jp2` `-dontwarn` is not hiding a regression.
- **targetSdk is 36 and the app is edge-to-edge.** No screen uses a
  `Scaffold`; insets are applied once around the nav host in `MainActivity`.
  New screens need nothing, but a screen that genuinely wants to draw under
  the system bars has to opt out there rather than locally.
- ~~**Browse is slow to open on a long book.**~~ Measured and fixed on
  2026-09-09 — see the section above. It was the text parse running on the
  main thread, in both reading screens.

**Store assets and copy are in the repo**: `docs/store/` (512 icon, feature
graphic, four screenshots), `docs/privacy-policy.md`, and
`docs/play-store-listing.md` with listing copy plus every App-content answer.
Screenshots are all Project Gutenberg books on purpose — the books that were
on the test device render in-copyright text in Browse and one carries an
"Anna's Archive" suffix in its filename, which reads to a Play reviewer like
a listing that supplies pirated books. Regenerate the feature graphic with
`python tools/gen_feature_graphic.py`; it derives from the icon file so the
two cannot drift.

**Nothing has actually been released yet.** `RELEASING.md` is the runbook.
Still outstanding, all of it on the Play Console side and none of it in code:
- No upload keystore exists, so `assembleRelease` silently produces
  **unsigned** artifacts locally. (In CI that now fails the build instead —
  a missing secret made `base64 -d` write an empty keystore and ship
  unsigned artifacts Play rejects hours later.)
- The four `RELEASE_*` Actions secrets are not set.
- GitHub Pages is not enabled, so the privacy-policy URL Play requires does
  not resolve. Settings → Pages → `main` / `/docs`.
- **`premium_unlock` does not exist in any Play Console.** Until it does the
  store returns no price and the upsell correctly hides its buy button —
  which means publishing the `play` flavour right now would ship two
  permanently locked features with no way to buy them. Create the product
  before publishing, or ship ungated.
- The purchase flow has never been exercised, and cannot be from a locally
  built APK — Play only serves billing to builds it installed. Needs an
  internal-testing upload plus licence testers.
- A personal developer account needs **12 testers opted into a closed test
  for 14 continuous days** before production can even be applied for. That
  is the long pole, measured in weeks; everything else above is hours.

## Book covers, narrow-screen fixes, and a TTS language picker (2026-08-24)

On [PR #8](https://github.com/loko087/LeerRapidon/pull/8) — **merged to
`main`** (2026-08-24). Four pieces, in commit order:

**1. Cover thumbnails.** Every imported book gets a small cover on its
`LibraryScreen` card, tried in this order:
1. **EPUB**: the cover image declared in the OPF manifest
   (`EpubParser.findCoverHref` in `EpubStructure.kt` — tries EPUB3
   `properties="cover-image"`, then EPUB2 `<meta name="cover">`, then an
   id/filename that looks like a cover). Pulled straight from the already-
   parsed zip entries in `TextExtractor.extractEpub`.
2. **PDF**: no embedded "cover" concept, so `TextExtractor.renderCover`
   renders page 1 with `PDFRenderer` (same library already used for OCR)
   at a DPI computed to land near a target width regardless of page size.
3. **Fallback (any source, including plain text/paste)**: a title search
   against the Open Library APIs — `OpenLibraryCovers.kt` hits
   `openlibrary.org/search.json?title=...` for a `cover_i`, then downloads
   `covers.openlibrary.org/b/id/<id>-L.jpg`. This is the app's **first
   network call ever** — added `INTERNET` permission. Runs in the
   background *after* the book is already saved (`BookRepository`'s own
   `repoScope`, not tied to any ViewModel), so a slow/absent network never
   blocks the import; the Flow-backed library list just updates in place
   if a match lands. Every failure path (no match, no network, timeout)
   returns null — never fails the import.
   Covers are normalized to an on-disk JPEG (`saveCover` in
   `BookRepository.kt`, capped at 480px wide — bigger than the ~44dp list
   thumbnail needs, but the same file also backs the tap-to-zoom preview
   below) at `<filesDir>/books/<id>.cover`, tracked via a new
   `BookEntity.coverPath` column (`AppDatabase` MIGRATION_2_3 — real
   migration, not destructive). `BookRepository.backfillMissingCovers()`
   runs once per app session (from `LibraryViewModel.init`) to retroactively
   fill in covers for books saved before this feature existed, preferring
   their preserved original file over an Open Library search.

**2. Tap-to-zoom cover preview.** Tapping a library card's cover opens it
centered and enlarged over a dark scrim (`CoverPreviewDialog` in
`LibraryScreen.kt`, a Compose `Dialog`). Dismiss via the X button, tapping
the scrim outside the image, or back (the `Dialog`'s own window intercepts
back for free) — tapping the image itself does nothing, only the
surrounding scrim dismisses.

**3. Two narrow-screen (360dp) bugs, found by testing at that width (the
dev emulator defaults to a wider ~360-390dp+ but still needs an explicit
`adb shell wm size` override to reproduce a truly compact phone) rather
than just the emulator's default:**
- The library card's source badge ("EPUB"/"PDF"/"TXT") could wrap
  vertically into one letter per line once the cover thumbnail left too
  little row width alongside the pre-existing Original/Delete buttons.
  Fixed by splitting Original/Delete onto their own row below the
  cover/title/badge row (same pattern as the existing "Browse" button fix
  in `ReaderScreen.kt` from PR #5), plus `maxLines`/`softWrap`/`overflow`
  on the badge text as a second line of defense.
- The RSVP single-word display clipped long words (e.g. "awareness"
  losing its last letter): the pre/post pivot-aligned columns in
  `ReaderScreen.kt` were a hardcoded 100dp regardless of how much wider
  the display box actually was (typically 130dp+ of unused room). Now
  sized from the box's real available width via `BoxWithConstraints`, with
  font-shrink (pre/post together via `rememberFittedWordFontSize`, so the
  pivot position doesn't jump) as a fallback for the rare word too long
  even for that. A deliberately pathological 25-letter test word still
  clips slightly at the 14sp floor — an accepted, appropriately-scoped
  limit, not something worth chasing further into illegibility.

**4. TTS reading-language picker.** `SpeechController` used to set
`tts.language = Locale.getDefault()` — the *device's* system language, not
the language of whatever's actually being read. On a phone set to Italian,
an English book got read with Italian pronunciation. Now defaults to
English (most likely to match an imported book regardless of device
language) and exposes `setLanguage()`/`availableLanguages()` for a new
"Language" row in the reader's Audio mode panel — a dropdown of every
installed language, shown as autonyms ("Italiano", not a translated name).
`availableLanguages()` combines `getVoices()` with a probe of common
languages via `isLanguageAvailable()`, since engines are inconsistent
about which of the two APIs they actually populate (confirmed on the test
emulator: `getVoices()` returns empty for a beat after the engine binds,
before lazily populating). Session-scoped like the existing
Speed/Words-per-frame/Audio mode settings — **not persisted** across
reader sessions; ask before changing that if a future request implies it
should be.

All four live-tested on the emulator (screenshots in the session
transcript, not reproduced here) — including a real end-to-end language
switch to Italian and back, and the narrow-screen fixes specifically
re-tested at a forced 360dp width where the original bugs reproduced.
**Not tested live:** the EPUB embedded-cover path (no test EPUB with a
cover on hand) — worth a spot-check with a real EPUB before treating it
as fully proven.

**Not done / explicitly out of scope:** no author-aware Open Library
search (title-only query — a generic title could mismatch), no manual
"search again"/"change cover" affordance, no cover preview on
`AddBookScreen` before saving, no persistence for the TTS language choice.

## Where things stand (2026-08-17)

All of the below is **merged into `main`** as of this writing (PRs #1–#5)
— this file previously carried "not yet committed" caveats for a couple
of these that no longer apply; if you're reading this from a stale copy,
trust `git log origin/main` over this paragraph.

- **Fast reading (RSVP)** and **original-form reading** (real PDF pages /
  EPUB chapters) both work. Original-form reading shipped in
  [PR #2](https://github.com/loko087/LeerRapidon/pull/2).
- Books only get the "Original" button if they were imported after that
  PR landed; older books don't have a preserved original file to show.
- **Words-per-frame RSVP mode** shipped in
  [PR #3](https://github.com/loko087/LeerRapidon/pull/3). A 1-5 slider
  next to Speed controls how many words flash together per frame; Audio
  mode respects the same frame size and highlights whichever word is
  actively being spoken. Global, session-only setting (like Audio mode),
  not persisted per-book.
  - Audio mode speaks the whole remaining book as one continuous TTS
    utterance (`ReaderViewModel.speakFromIdx`) — a per-frame-utterance
    version was tried first and reverted because engine startup latency
    per call made the Speed slider feel like it did nothing.
  - TTS rate is clamped to 3x (pre-existing, in `SpeechController`), so
    Audio mode stops getting faster above ~540-560 wpm — there's a UI
    note for this now, left in place at the user's request rather than
    raised or removed.
- **Browse full text** shipped in
  [PR #4](https://github.com/loko087/LeerRapidon/pull/4)
  (`BrowseTextScreen.kt` / `BrowseTextViewModel.kt`,
  `RsvpEngine.paragraphs()`). Shows the whole book as flowing paragraph
  text (reached via a "Browse" button next to "Original" in the reader),
  current word highlighted, tap any word to jump the RSVP reader there.
  `RsvpEngine.paragraphs()` groups the same words `tokenize()` produces
  (capped at 120 words/paragraph so a no-blank-lines source can't produce
  one giant unvirtualized `LazyColumn` item) — word order/count is
  provably identical to `tokenize()`, so a paragraph word's index is a
  valid RSVP word index. Picking a word persists it via
  `BookRepository.updateProgress` then forces a fresh reader instance
  (same `navigateMode` mechanism as mode switches) to pick it up.
- **Narrow/short-screen layout fixes** shipped in
  [PR #5](https://github.com/loko087/LeerRapidon/pull/5), found by
  testing on a real phone (a Galaxy Z Flip5, 360dp portrait width) rather
  than only the wider dev emulator:
  - `LibraryScreen`'s book title had no width limit and could push the
    delete button (and "Original") off the edge of the card on a long
    title — title now wraps/ellipsizes within `weight(1f)`, actions stay
    on a fixed-size trailing row.
  - `ReaderScreen` was a non-scrolling `Column`; in landscape there
    wasn't enough height left for the Speed/Words-per-frame/Audio mode
    panel and no way to reach it. Now wrapped in `verticalScroll`.
  - An unusually long single word (no spaces) wrapped across two lines
    inside the fixed-width pivot-letter columns and overlapped. Those
    `Text`s are now `softWrap = false` with clipping — degrades to one
    clipped line instead of garbling.
  - **Takeaway for future UI work**: the dev emulator used through most
    of this project is noticeably wider (~448dp) than a typical/compact
    phone (~360dp). Layout changes that look fine there aren't proof
    against overflow on a real device — worth spot-checking a narrow
    width (`adb shell wm size <w>x<h>`) and both orientations before
    calling a screen change done.

## Next planned feature: font type & size, for both reading modes

User wants to control font family and font size, applied consistently
in **both** the RSVP reader (`ReaderScreen.kt`) and the Browse full-text
view (`BrowseTextScreen.kt`) — one shared setting, not independent
per-screen choices.

**Not yet decided — needs a real design conversation before building:**
- **Scope/persistence:** global session-only (like `wordsPerFrame`/
  `audioMode` today — resets each time the reader reopens) or something
  that actually persists across app restarts? Font choice feels more
  like a lasting preference than a per-session toggle, unlike the
  precedents so far — worth asking rather than assuming either way.
  There's still no persistence mechanism for a cross-screen shared
  setting in `main` (`wpm` persists but per-book via Room, which isn't
  the right shape for a single UI-wide preference). Note the billing
  work added a `SharedPreferences` — but only inside the **`play`
  flavour's** `PremiumProvider`, so it is not reachable from shared code
  and is not the mechanism to build on. See the release section at the
  top of this file.
- **Font type options:** a curated preset list (e.g. serif/sans/mono,
  matching `FontFamily.Serif` already used for the RSVP word display) vs.
  exposing more of the system's available fonts?
- **Font size interaction with existing scaling:** `ReaderScreen.kt`
  already auto-shrinks `frameFontSize` as `wordsPerFrame` grows (40sp at
  1 word down to 22sp at 5). A user font-size preference needs to
  compose with that, not just replace it — e.g. as a multiplier/base
  size rather than a fixed sp value.
- **Where the controls live:** extend the existing settings panel
  (alongside Speed / Words per frame / Audio mode) in the reader, and
  something analogous in the Browse screen's top bar?

## Next planned feature: page/section navigation for the fast reader

Right now the RSVP reader's only way to jump around is a single
continuous `Slider` over the raw word index
(`ReaderScreen.kt` / `ReaderViewModel.kt`). That makes it hard to tell
*where in the book* a given position actually is — it's just a
percentage of total words, with no sense of chapter or section.

**Why it matters:** the user specifically called this out as hard to use
once a book is book-length (hundreds of pages).

**Not yet decided — needs a real design conversation before building:**
what a "page" or "section" should mean here. Candidate approaches:
- Fixed word-count chunks (simplest, works for any source, doesn't
  respect real structure)
- Real chapter/heading boundaries, using the `EpubParser` spine data that
  now exists (from the original-form reading work) for EPUB sources
- Something PDF-specific, since PDFs have real page boundaries already
  used by the original-form PDF viewer

Don't assume one of these and build it — confirm the approach with the
user first, since it changes the UI (replacing a slider) and possibly
the data model (tracking position per-section instead of a raw word
index).

## Nice to have: persist the TTS reading-language choice

The language picker added in [PR #8](https://github.com/loko087/LeerRapidon/pull/8)
(`ReaderViewModel.setLanguage` / `ReaderScreen.kt`'s "Language" row) is
session-only, same as `wordsPerFrame`/`audioMode` today — it resets to
English every time a book is reopened. User explicitly deferred deciding
on persistence here ("let's leave that for the 'what to do next', like a
nice to have") rather than asking for it now.

**Not yet decided — needs a real design conversation before building,**
same open question as the font-options item above (shared code still has
no cross-screen/cross-session preference store — just per-book Room
columns; the one `SharedPreferences` that now exists lives in the `play`
flavour's billing code and is not shared):
- Global (one language for all books) or per-book (a German book and an
  Italian book each remember their own)? Per-book fits the actual
  problem better — the language is a property of the text, not a
  standing user preference — but needs a new `BookEntity` column +
  migration rather than reusing whatever mechanism the font settings end
  up with.
- Worth building both this and font persistence on the same underlying
  mechanism if/when either gets picked up, rather than solving storage
  twice.
