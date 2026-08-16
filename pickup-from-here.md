# Pickup from here

Notes for whichever session (on whichever machine) picks this project back
up next. Local Claude memory doesn't travel between computers, so anything
that should survive a machine switch belongs here instead.

## Where things stand (2026-08-16)

- **Fast reading (RSVP)** and **original-form reading** (real PDF pages /
  EPUB chapters) both work. Original-form reading shipped in
  [PR #2](https://github.com/loko087/LeerRapidon/pull/2) — check whether
  it's merged yet.
- Books only get the "Original" button if they were imported after that
  PR landed; older books don't have a preserved original file to show.

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
