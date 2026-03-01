# apk-ebiten-builder

Build **any Ebiten** game as an **Android APK** using a simple `Makefile` workflow — **without Android Studio**.

This repository provides a reusable Android/Gradle template + build rules (`Include.mk`) that:

1. Generate an Android project from templates,
2. Build an Android library (`.aar`) from your Go **`package mobile`** using `ebitenmobile bind`,
3. Assemble a debug APK with Gradle,
4. Optionally install and launch it on a connected device via `adb`.

This approach was heavily informed by practical learnings from `github.com/programatta/demoandroid` (especially the “do it by hand / no Android Studio” style and the minimal Gradle + manifest structure).
