# Releasing Leer Rapidon

Two channels, one build. GitHub gets a signed APK people can sideload;
Play gets an AAB. Both are signed with the same upload key.

---

## One-time setup

### 1. Create the upload key

From the repo root:

```
keytool -genkeypair -v -keystore rapidreader-upload.jks \
  -alias rapidreader -keyalg RSA -keysize 2048 -validity 10000
```

Then `cp keystore.properties.example keystore.properties` and fill in the
passwords you just chose. Both files are gitignored.

**Back up the .jks and the passwords somewhere off this machine.** Once
Play App Signing is enabled, a lost upload key can be reset by Google —
but only after the first upload, and only via a support request. Losing it
before then means starting the listing over.

### 2. Add the CI secrets

In **GitHub → Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 rapidreader-upload.jks` |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | `rapidreader` |
| `RELEASE_KEY_PASSWORD` | key password |

### 3. Publish the privacy policy

Play will not accept a submission without a reachable policy URL. Enable
**GitHub → Settings → Pages → Deploy from branch → `main` / `/docs`**,
which serves `docs/privacy-policy.md` at
`https://loko087.github.io/LeerRapidon/privacy-policy`. Load the URL in a
browser before pasting it into the Console.

### 4. Play Console account

$25, one-time. **Start this first — it is the long pole.** A personal
developer account has to run a closed test with at least 12 testers
opted in for 14 continuous days before it can even apply for production
access. Recruit the testers before you need them.

---

## Cutting a release

1. Bump `versionCode` (always +1) and `versionName` in
   [app/build.gradle.kts](app/build.gradle.kts). Play rejects a reused
   `versionCode`, and it has to keep rising across both channels — so
   bump it even for a GitHub-only build.
2. Commit, then tag and push:

```
git tag v1.0.0 && git push origin v1.0.0
```

3. The `Release` workflow builds and signs both artifacts, then opens a
   **draft** GitHub release with the APK attached. Review the generated
   notes and publish it manually.
4. Download the `play-bundle` workflow artifact and upload the `.aab` in
   the Play Console.

### Minification

The release build runs R8 with resource shrinking. R8 can only break this app
in the reflective paths, so a green compile is **not** evidence the build
works — after any dependency or keep-rule change, install a signed release
build and import one of each: a PDF with a text layer (PdfBox), a scanned PDF
(ML Kit OCR), and an EPUB.

To test a release build without the real upload key, sign it with the local
debug key:

```
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android   --ks-key-alias androiddebugkey --key-pass pass:android app-release-unsigned.apk
```

### Building locally instead

```
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab
```

With no `keystore.properties` present, both still build but come out
**unsigned** — that is deliberate so a clean clone works, but check the
build log before shipping anything.

---

## Still outstanding

### Premium unlock (not built)

The app currently has no billing code at all — free and premium are
identical. Shipping the one-time unlock needs:

- `com.android.billingclient:billing-ktx`, and a `premium_unlock` managed
  product created in the Console.
- Purchase **acknowledgement within 3 days** — Play auto-refunds anything
  unacknowledged, so this is not optional.
- Restore-on-reinstall by querying existing purchases at launch.
- A decision on **which features sit behind the wall.** Nothing is gated
  today, and this is a product call, not a technical one.
- A story for the GitHub APK, which cannot use Play billing at all: either
  it stays permanently free-tier, or it ships as a separate build flavour
  with everything unlocked. The PolyForm Noncommercial licence already
  stops anyone reselling either one.

### Store listing assets

Icon, feature graphic and four screenshots are in `docs/store/`, and the
listing copy and every App-content answer are in
[docs/play-store-listing.md](docs/play-store-listing.md). Still to decide
there: the price of the premium product.
