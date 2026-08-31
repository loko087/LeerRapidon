---
layout: default
title: Privacy Policy
permalink: /privacy-policy
---

# Leer Rapidon — Privacy Policy

**Last updated: 31 August 2026**

Leer Rapidon is developed by Jose Pablo Monge. This policy covers the
Leer Rapidon Android app (package `com.rapidreader.app`).

## The short version

Leer Rapidon has no account, no analytics, no advertising, and no crash
reporting. Your books, your reading positions, and your settings never
leave your device. The app makes exactly one kind of network request, and
it sends only a book title — never anything that identifies you.

## What stays on your device

Everything you put into the app:

- The PDF, EPUB, or text files you import, and the text extracted from
  them. Both are written to the app's private internal storage, which
  other apps cannot read.
- Book titles, reading positions, reading speed, and cover images, stored
  in a local database on the device.

None of this is uploaded, backed up to our servers, or shared with anyone.
We do not operate any server that receives your data. Uninstalling the app
deletes all of it.

Leer Rapidon reads a file you import only because you picked it yourself
through the Android file picker. It has no permission to browse your
storage on its own, and it does not scan your device for documents.

## The one network request

When you import a book, Leer Rapidon first tries to get a cover image out
of the file itself — an embedded EPUB cover, or a rendered first page for
a PDF. If that fails, and only then, it asks the public
[Open Library](https://openlibrary.org) API whether it has a cover for a
book by that title.

That request sends:

- **The book's title**, as a search term, over HTTPS to `openlibrary.org`
  and `covers.openlibrary.org`.

That request does **not** send: your name, email, device identifiers, an
advertising ID, your location, the contents of the book, or any account
information. There is no account to send.

Open Library is operated by the Internet Archive, and is subject to
[their privacy policy](https://archive.org/about/terms.php). Like any web
request, it will expose your IP address to their servers.

If you never import a book whose cover has to be looked up, the app makes
no network requests at all.

## Text-to-speech

Audio mode uses the text-to-speech engine already installed on your
device. Leer Rapidon hands the text to Android's `TextToSpeech` API. What
happens next depends on which engine you have installed and how it is
configured — some engines synthesise entirely on-device, others may use
the vendor's servers. Leer Rapidon does not choose or control this. Check
your device's text-to-speech settings if that distinction matters to you.

## Payments

If you buy the one-time premium unlock, the purchase is handled entirely
by Google Play billing. Leer Rapidon never sees or stores your payment
details; it only receives a signal from Play that the purchase exists.
Google's handling of that transaction is covered by the
[Google Payments privacy notice](https://payments.google.com/legaldocument?family=0.privacynotice).

## Children

Leer Rapidon is not directed at children and does not knowingly collect
any information from anyone. There is nothing to collect.

## Changes

If this policy changes, the updated version will be published at this
same URL with a new "last updated" date.

## Contact

Questions about this policy: open an issue at
<https://github.com/loko087/LeerRapidon/issues>, or email
jospablo@gmail.com.
