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

### The two flavours

There is one codebase and two distributions, split by product flavour
because a sideloaded build cannot use Play Billing at all:

| Flavour | Goes to | Premium features |
|---|---|---|
| `github` | the APK on the Releases page | unlocked, no billing code linked |
| `play` | the AAB in the Play Console | behind the one-time purchase |

Audio mode and the original-form reader are what the purchase unlocks. Each
flavour supplies its own `PremiumProvider` from `app/src/<flavour>/java`.

### Building locally instead

```
./gradlew assembleGithubRelease   # app/build/outputs/apk/github/release/app-github-release.apk
./gradlew bundlePlayRelease       # app/build/outputs/bundle/playRelease/app-play-release.aab
```

With no `keystore.properties` present, both still build but come out
**unsigned** — deliberate, so a clean clone works. In CI that would be a
silent disaster instead of a convenience, so a release build there fails
outright when the signing secrets are missing rather than quietly producing
an unsigned artifact Play will reject hours later.

---

## Still outstanding

### Premium unlock — built, not yet sellable

The billing integration is done: `premium_unlock` is queried and purchased
through Play Billing, purchases are acknowledged (Play auto-refunds anything
left unacknowledged for three days), and entitlement is restored on reinstall
by querying existing purchases. Entitlement is cached to disk so a paying
user is not shown a paywall when the app is offline — which, for an app that
otherwise never touches the network, is the normal case rather than the edge
case.

What is still missing is on the Play Console side:

- **Create the `premium_unlock` managed product** and set a price. Until it
  exists, the store returns no price and the upsell dialog deliberately shows
  no buy button rather than a button that cannot work.
- **Add licence testers** (Console → Setup → Licence testing) so you can run
  the purchase flow end to end without being charged. The purchase flow
  cannot be tested from a locally-built APK at all — it needs a build
  installed by Play, so upload to internal testing first.

There is no server-side receipt verification, because there is no server.
For a single cheap one-time product on an otherwise offline app, standing up
a backend to defend it would cost more than the leakage.

### Store listing assets

Icon, feature graphic and four screenshots are in `docs/store/`, and the
listing copy and every App-content answer are in
[docs/play-store-listing.md](docs/play-store-listing.md). Still to decide
there: the price of the premium product.
