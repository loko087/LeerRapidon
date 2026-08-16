# Rapid Reader (Native Android)

RSVP speed-reading app with a book library, PDF/EPUB import, and native
text-to-speech, built with Kotlin + Jetpack Compose. Fully offline —
no network permission is requested.

## What's implemented

- **Library** (`LibraryScreen.kt`) — Room-backed list of books with
  progress bars and last-read time.
- **Add a book** (`AddBookScreen.kt`) — file picker (PDF / EPUB / TXT)
  or paste text directly.
- **Extraction** (`extract/TextExtractor.kt`):
  - PDF via PdfBox-Android's `PDFTextStripper`. If the PDF has no text
    layer (a scanned/image-only page), it falls back to rendering each
    page and running ML Kit's bundled, on-device Text Recognition over it
    — no network call, so the app stays fully offline.
  - EPUB via a manual ZIP walk + `container.xml`/OPF parsing to read the
    spine in order, with a tolerant HTML-to-text pass per chapter file
    (real-world EPUB markup is often not strictly valid XML, so this
    avoids brittle XML parsing failures on content documents).
  - Plain text read directly.
- **Fast reader** (`ReaderScreen.kt` + `ReaderViewModel.kt`) — RSVP display
  with the ORP (optimal recognition point) letter highlighted, a speed
  slider, scrubber, and audio mode.
- **Original-form reader** — a second reading mode that shows a PDF or
  EPUB as it actually looks, reachable via an "Original" button on a
  book's library card or from inside the fast reader (only shown for
  books that have one — see Persistence below):
  - **PDF** (`PdfViewerScreen.kt` + `PdfViewerViewModel.kt`) — swipe
    between pages, rendered by Android's native `PdfRenderer` (not
    PdfBox — see the code comment on the renderer choice), with
    pinch-zoom and pan on each page.
  - **EPUB** (`EpubViewerScreen.kt` + `EpubViewerViewModel.kt`) — a
    `WebView` showing one spine item (chapter) at a time, with native
    pinch-zoom and Prev/Next chapter buttons. "Pages" are chapters, not
    fixed print-style pagination. JavaScript is deliberately disabled —
    EPUB content is untrusted, user-supplied HTML.
- **Audio mode** (`tts/SpeechController.kt`) — wraps Android's native
  `TextToSpeech` and uses `onRangeStart` (API 26+) for genuine
  word-boundary sync, so the display advances with the voice rather than
  a fallback timer. Fast-reader only, for now.
- **Persistence** — `BookRepository` stores each book's extracted text as
  a file under internal storage and keeps title/progress/speed in a Room
  database. Reading position is saved every ~15 words and on
  pause/scrub, so reopening a book resumes exactly where you left off.
  For PDF/EPUB imports, the original file is also preserved on-device
  (`OriginalStore`) so the original-form reader has something to show;
  pasted text and plain `.txt` imports have no "original" to preserve.
  The original-form reader tracks its own position (page or chapter)
  separately from the fast reader's word-index progress, since the two
  don't map onto each other.

## Known limitations

- **Scanned PDFs** are read via on-device OCR when there's no embedded
  text layer, which is slower and less accurate than a real text layer —
  expect more misreads (especially for low-resolution scans, unusual
  fonts, or non-Latin scripts, since the bundled model targets Latin
  script).
- **TTS voice availability** depends on what's installed on the device;
  if none is available for the detected language, audio mode falls back
  to visual-only automatically.
- **Storage cost**: preserving the original PDF/EPUB alongside the
  extracted text means imports now use roughly double the space of the
  source file. There's a free-space guard at import time, but no UI yet
  showing how much space a book's original is using.
- **Books imported before this feature shipped** have no original file
  saved, so they only show the fast reader — no "Original" button. This
  can't be back-filled; the source bytes were never kept.
- No cloud sync — everything lives in the app's private storage on-device.

## AI  USAGE 

I am using Claude Code to help me make decisions on architecture, review code and debug errors. 
