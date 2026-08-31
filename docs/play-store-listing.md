# Play Console listing — copy and answers

Everything the Console asks for, in one place. Character limits are Play's.

---

## Main store listing

**App name** (30 chars max)

```
Leer Rapidon
```

> The launcher icon, `strings.xml`'s `app_name`, the in-app header, and this
> listing all say **Leer Rapidon**. The package name stays
> `com.rapidreader.app` — it is permanent once published and is never shown
> to users, so it is deliberately not being changed. Internal Kotlin symbols
> (`RapidReaderTheme`, `Theme.RapidReader`) are likewise left alone.
>
> Search Play for the name before you submit; it is changeable later, the
> package name is not.

**Short description** (80 chars max)

```
Speed-read your own PDFs and EPUBs offline — RSVP reader with text-to-speech.
```

**Full description** (4000 chars max)

```
Leer Rapidon turns the books you already own into a faster read.

Import a PDF, EPUB, or text file and Leer Rapidon flashes it to you one
word at a time — the RSVP technique — with the optimal recognition point
of each word highlighted so your eyes never have to move. No scanning
across lines, no losing your place. Most people comfortably read two to
three times their usual pace within a few sessions.

WHAT IT DOES

• Speed reader with a live speed slider, from a gentle warm-up pace up to
  900 words per minute.
• Words-per-frame control — show one word at a time, or up to five, and
  the highlight stays on the word you are actually reading.
• Audio mode built on your device's own text-to-speech, synced word by
  word with the display so you can listen and follow along at once.
• Browse view shows the whole book as normal flowing text whenever you
  want to slow down. Tap any word to jump the speed reader there.
• Original view renders your PDF page by page, or your EPUB chapter by
  chapter, exactly as it was laid out — pinch to zoom, swipe to turn.
• Your library remembers where you stopped in every book, down to the
  word, and picks up there next time.
• Scanned PDFs with no text layer are read using on-device OCR, so
  image-only documents still work.

BUILT TO STAY OUT OF YOUR WAY

No account. No sign-up. No ads, anywhere, ever — not in the free version
and not in the premium one. Nothing to dismiss and nothing tracking you.

Your books never leave your phone. They are stored in the app's private
storage and are never uploaded. The app works with no internet connection
at all; the only network request it ever makes is an optional cover-art
lookup by book title when a cover cannot be pulled from the file itself.

PREMIUM

Leer Rapidon is free to use. The speed reader, the library, the browse
view and PDF/EPUB import are all free, with no ads and no time limit. A
single one-time purchase unlocks audio mode and the original-form reader —
no subscription, no recurring charge, and it stays unlocked on any device
signed in to the same Google account.

Leer Rapidon reads the files you give it. It is not a store and does not
sell, supply, or link to any books.
```

---

## Graphics

All present. Regenerate the banner with `python tools/gen_feature_graphic.py`
— it derives the badge and the gradient from the icon file, so the two cannot
drift apart.

| Asset | Spec | File |
|---|---|---|
| App icon | 512 × 512, no transparency | `app/src/main/ic_launcher-playstore.png` — verified rendering correctly under the launcher's circular mask |
| Feature graphic | 1024 × 500, no transparency | `store/feature-graphic-1024x500.png` |

### Screenshots

Captured from a Pixel 8 Pro emulator at 1344 × 2992, off the **minified
release build**, so they show what ships.

| File | Shows |
|---|---|
| `store/01-library.png` | Library with three books and their embedded cover art |
| `store/02-fast-reader.png` | RSVP frame at 3 words, pivot letter highlighted, speed and words-per-frame controls |
| `store/03-browse-text.png` | Browse full-text view, current word highlighted in place |
| `store/04-original-epub.png` | Original-form EPUB view, chapter 1 with the illustrated cover |

Every one uses Project Gutenberg books — *Pride and Prejudice*,
*Frankenstein*, *Alice's Adventures in Wonderland*. **Keep it that way.**
Screenshots taken from the books that were on the test device before are not
usable: Browse renders a full page of in-copyright text, and one imported
filename carries an "Anna's Archive" suffix that shows in the header. Play
reviewers read screenshots, and a listing that looks like it supplies pirated
books is a fast rejection.

Worth adding later, from the same books: audio mode with the checkbox ticked,
and the PDF original view.

## App content declarations

| Section | Answer |
|---|---|
| **Privacy policy** | `https://loko087.github.io/LeerRapidon/privacy-policy` (enable GitHub Pages from `/docs` on `main` first) |
| **App access** | All functionality available without special access — no login |
| **Ads** | **No**, the app contains no ads |
| **Content rating** | Reference/Books. No violence, sexuality, profanity, gambling, or user-to-user communication → expect *Everyone / PEGI 3* |
| **Target audience** | 18+ (or 13+). Do **not** opt into Designed for Families — that adds review burden for no benefit here |
| **News app** | No |
| **COVID-19 contact tracing** | No |
| **Data safety** | See below |
| **Government apps** | No |
| **Financial features** | No |
| **Health** | No |

### Data safety form

Play asks whether you *collect* or *share* data. Collection means it
leaves the device to a server you control; on-device-only storage is not
collection.

- **Does your app collect or share any of the required user data types?**
  → **No.**

The book title sent to Open Library is worth understanding before you
answer: it goes to a third party (the Internet Archive), but it is not
tied to any identifier, is not persisted by us, and Play's definition
scopes "collection" to the listed personal/sensitive data types — a book
title is none of them. If you would rather be conservative, the honest
alternative is to declare **App activity → Other actions**, collected,
not shared, optional, for app functionality. Either answer is defensible;
pick one and make sure the privacy policy matches it. It already
describes the request in full.

- **Is all data encrypted in transit?** → Yes (HTTPS).
- **Data deletion** → Uninstalling removes everything; no server-side
  data exists to delete.

---

## Premium in-app product

Create under **Monetise → Products → In-app products**. The app already
queries and purchases this ID; until the product exists the store returns no
price and the upsell shows no buy button.

| Field | Value |
|---|---|
| Product ID | `premium_unlock` (must match the app exactly; permanent once created) |
| Type | One-time, **Managed product** (not a subscription, not consumable) |
| Name | Leer Rapidon Premium |
| Description | Unlocks audio mode and the original-form reader, forever. One payment, no subscription. |
| Price | ⬜ **still to decide** |

### What the purchase unlocks

| Feature | Free | Premium |
|---|---|---|
| RSVP speed reader, speed and words-per-frame | ✅ | ✅ |
| Library, PDF/EPUB/text import, OCR for scanned PDFs | ✅ | ✅ |
| Browse full-text view | ✅ | ✅ |
| **Audio mode** (text-to-speech with word sync) | — | ✅ |
| **Original view** (page-faithful PDF / EPUB) | — | ✅ |

The sideloaded GitHub APK is a separate `github` flavour with everything
unlocked and no billing code linked at all. That is deliberate: Play Billing
only works for apps installed by the Play Store, and that distribution exists
for people who would rather not use the Store. The PolyForm Noncommercial
licence stops anyone reselling either build.

### Before you can test a purchase

Add licence testers under **Setup → Licence testing**, then upload a build to
internal testing. The purchase flow cannot be exercised from a locally-built
APK — Play only serves billing to apps it installed itself.
