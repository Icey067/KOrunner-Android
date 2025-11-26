# KOrunner-Android

KOrunner-Android is a lightweight Android project for running Kotlin-based utilities and experiments.

## Overview

This repository contains an Android app module written in Kotlin, Gradle configuration, and unit tests. It is intended as a starting point for experiments and small utilities.

## Features

- Simple Kotlin Android app structure
- Gradle build configuration
- Unit tests and lint checks

## Quick Start

Prerequisites:

- JDK 11 (recommended)
- Android SDK (via Android Studio or `sdkmanager`)
- A device or emulator to install the debug build

Clone the repository:

```bash
git clone https://github.com/Icey067/KOrunner-Android.git
cd KOrunner-Android
```

Open the project in Android Studio (recommended) or build from the command line:

```bash
./gradlew clean assembleDebug
```

Install to a connected device (debug build):

```bash
./gradlew installDebug
```

Run unit tests locally:

```bash
./gradlew testDebugUnitTest
```

Run lint and checks:

```bash
./gradlew lint
./gradlew check
```

## Continuous Integration

This repository includes a GitHub Actions workflow at `.github/workflows/android-ci.yml` that runs on pushes and pull requests. The workflow performs a Gradle build, runs unit tests and lint to provide automated review feedback on PRs.

Build status badge:

[![CI](https://github.com/Icey067/KOrunner-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Icey067/KOrunner-Android/actions/workflows/android-ci.yml)

If you want additional automated checks (e.g., code scanning, dependency updates), consider enabling GitHub Dependabot and CodeQL under the repository settings.

## Contributing

- Fork the repository and open a pull request for changes.
- Keep changes focused and add/update tests where applicable.
- Run `./gradlew check` before submitting a PR.

If you want a PR template or `CONTRIBUTING.md`, I can add one.

## Project Layout

- `app/` — Android app module
- `gradle/` — Gradle config and version catalogs

## License

This repository does not include a license file. If you'd like, I can add an MIT or Apache-2.0 license. Please tell me which license you prefer.

## Contact

Maintainer: `Icey067` — open issues or PRs on the repository.
