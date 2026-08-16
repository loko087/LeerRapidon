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
- **Reader** (`ReaderScreen.kt` + `ReaderViewModel.kt`) — RSVP display
  with the ORP (optimal recognition point) letter highlighted, a speed
  slider, scrubber, and audio mode.
- **Audio mode** (`tts/SpeechController.kt`) — wraps Android's native
  `TextToSpeech` and uses `onRangeStart` (API 26+) for genuine
  word-boundary sync, so the display advances with the voice rather than
  a fallback timer.
- **Persistence** — `BookRepository` stores each book's extracted text as
  a file under internal storage and keeps title/progress/speed in a Room
  database. Reading position is saved every ~15 words and on
  pause/scrub, so reopening a book resumes exactly where you left off.

## Known limitations

- **Scanned PDFs** are read via on-device OCR when there's no embedded
  text layer, which is slower and less accurate than a real text layer —
  expect more misreads (especially for low-resolution scans, unusual
  fonts, or non-Latin scripts, since the bundled model targets Latin
  script).
- **TTS voice availability** depends on what's installed on the device;
  if none is available for the detected language, audio mode falls back
  to visual-only automatically.
- No cloud sync — everything lives in the app's private storage on-device.

## AI  USAGE 

I am using Claude Code to help me make decisions on architecture, review code and debug errors. 
