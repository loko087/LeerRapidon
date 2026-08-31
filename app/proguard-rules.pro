# R8 rules for the release build.
#
# Most of what this app depends on ships its own consumer rules, which R8
# applies automatically — Room, Compose, coroutines, and both ML Kit
# artifacts (which keep native method names to avoid UnsatisfiedLinkError).
# PdfBox-Android also ships the keep rule for its reflective SecurityHandler
# lookup. So the only thing left here is silencing references to desktop-Java
# and BouncyCastle classes that PdfBox links against but never reaches on
# Android — without these R8 fails the build on missing classes.
#
# The PDF, OCR, and EPUB paths are the ones minification can plausibly break,
# so verify a real import of each against a minified build, not just a
# successful compile.

# PdfBox's JPXFilter calls an optional JPEG-2000 codec that this app does not
# depend on. It was already absent before minification, so JPX-encoded images
# in PDFs were never decodable — R8 just makes the missing reference fatal.
-dontwarn com.gemalto.jp2.**

-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.naming.**
-dontwarn javax.xml.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.commons.logging.**

# Keeps stack traces from minified crash reports readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
