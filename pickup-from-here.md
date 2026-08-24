# Pickup from here

Notes for whichever session (on whichever machine) picks this project back
up next. Local Claude memory doesn't travel between computers, so anything
that should survive a machine switch belongs here instead.

## Book cover thumbnails (2026-08-24) — built, not yet committed/pushed

Every imported book now gets a small cover thumbnail on its `LibraryScreen`
card, tried in this order:
1. **EPUB**: the cover image declared in the OPF manifest
   (`EpubParser.findCoverHref` in `EpubStructure.kt` — tries EPUB3
   `properties="cover-image"`, then EPUB2 `<meta name="cover">`, then an
   id/filename that looks like a cover). Pulled straight from the already-
   parsed zip entries in `TextExtractor.extractEpub`.
2. **PDF**: no embedded "cover" concept, so `TextExtractor.renderCover`
   renders page 1 with `PDFRenderer` (same library already used for OCR)
   at a DPI computed to land near 300px wide regardless of page size.
3. **Fallback (any source, including plain text/paste)**: a title search
   against the Open Library APIs — `OpenLibraryCovers.kt` hits
   `openlibrary.org/search.json?title=...` for a `cover_i`, then downloads
   `covers.openlibrary.org/b/id/<id>-M.jpg`. This is the app's **first
   network call ever** — added `INTERNET` permission. Runs in the
   background *after* the book is already saved (`BookRepository`'s own
   `repoScope`, not tied to any ViewModel), so a slow/absent network never
   blocks the import; the Flow-backed library list just updates in place
   if a match lands. Every failure path (no match, no network, timeout)
   returns null — never fails the import.

Storage: covers are normalized to a small on-disk JPEG (`saveCover` in
`BookRepository.kt`, capped at 300px wide) at `<filesDir>/books/<id>.cover`,
tracked via a new `BookEntity.coverPath` column
(`AppDatabase` MIGRATION_2_3 — real migration, not destructive).

**Live-tested on the emulator** (not just compiled): pasted-text import
("Pride and Prejudice") confirmed the Open Library fallback finds and
displays a real cover asynchronously after save; a hand-built minimal PDF
confirmed the first-page-render path shows actual rendered page content
as the cover. The EPUB embedded-cover path compiled cleanly but wasn't
live-tested (no test EPUB with a cover on hand) — worth a spot-check with
a real EPUB before considering that path fully proven.

**Not done / explicitly out of scope this round:** no author-aware search
(title-only query to Open Library — a generic title could mismatch), no
manual "search again" / "change cover" affordance if the auto-fetch picks
a wrong or no cover, no cover preview on the `AddBookScreen` before saving.

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
  There's no persistence mechanism for a cross-screen shared setting
  yet (no SharedPreferences/DataStore in this codebase currently; `wpm`
  persists but per-book via Room, which isn't the right shape for a
  single UI-wide preference).
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
