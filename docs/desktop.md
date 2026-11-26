# Grimoire Desktop

The Grimoire Desktop application has moved to its own repository:
[`Operation-Grimoire/grimoire-desktop`](https://github.com/Operation-Grimoire/grimoire-desktop).

This repository continues to own the Android application (`:app`), its UI
library (`:shared-ui`), and the shared JVM reading engine (`:reading-core`),
which is published as `io.grimoire:reading-core` to GitHub Packages for
consumption by `grimoire-desktop`'s headless JVM sidecar.

Historical design notes for the shared storage and extension contracts remain in
[shared-storage.md](shared-storage.md), [desktop-storage.md](desktop-storage.md),
and [desktop-extensions.md](desktop-extensions.md).
