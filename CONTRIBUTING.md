# Contributing to LeerRapidon

Thanks for wanting to help out. A few things to know before opening a PR.

## License

LeerRapidon is source-available under the [PolyForm Noncommercial License
1.0.0](./LICENSE) — free to use, study, and modify for any noncommercial
purpose. Commercial use requires a separate license from the project
maintainer.

**By submitting a contribution (a pull request, patch, or any other
proposed change), you agree that:**

1. You have the right to submit it — it's your own original work, or
   you otherwise have the right to contribute it under these terms.
2. Your contribution is licensed to the project under the same PolyForm
   Noncommercial License 1.0.0 terms as the rest of the codebase.
3. You grant the project maintainer the right to relicense your
   contribution, including for commercial purposes — the same right the
   maintainer holds over the rest of the codebase. This keeps the
   project's licensing consistent: no single contribution ends up under
   different terms than everything around it.

This is a lightweight, informal version of what larger projects handle
with a signed Contributor License Agreement. It's proportionate to a
small open project — if LeerRapidon ever takes on significant outside
contribution, a proper CLA process would be worth setting up alongside
actual legal review.

If you're not comfortable with these terms, you're still welcome to fork
the project under the existing license — you just wouldn't be
contributing changes back upstream.

## Making a change

1. Open an issue first for anything nontrivial — saves everyone time if
   the approach needs discussion before code gets written.
2. Fork, branch, and keep PRs focused on one thing. Smaller PRs get
   reviewed faster.
3. Match the existing architecture — see `ROADMAP.md` for the current
   layer structure (`data / domain / reading / ui`) and the locked-in
   architecture decisions before adding something new. In particular:
   the engine/renderer split for reading modes, and the offline-first
   constraint (no feature should silently require a network connection
   to use content already imported).
4. Test on a real device or emulator before opening the PR — `ROADMAP.md`
   has a running list of real bugs found this way (large-file handling,
   TTS edge cases); it's the pattern that's worked so far.

## Reporting bugs

Include Android version, device (or emulator config), and — if it's a
crash — the logcat output around the failure. Stack traces have been the
difference between guessing and actually finding root causes for every
bug fixed in this project so far.
