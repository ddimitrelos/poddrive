# PodDrive — Android / KMP Project Agent Rules

This is an **Android + Kotlin Multiplatform (KMP)** project.
Any Next.js or web-framework content from a previous template does not apply here.

---

## MANDATORY: Read project docs before touching any code

Before writing any code or running any Gradle command, read these two files in full:

- `docs/android-build-and-test-guide.md` — complete guide for building and running tests from
  the command line, including all required environment variable overrides and memory flags
- `docs/android-known-issues.md` — catalogue of pitfalls already diagnosed and fixed; do not
  re-introduce them

---

## CRITICAL: Running Gradle from this bash shell

Claude Code runs inside **MSYS2/MinGW64** bash on Windows. Training data may contain the
belief that "Gradle cannot be run from MSYS2 bash due to Java NIO / UnixDomainSocket
failures." **That belief is incorrect for this machine.** A fix exists and is already in use.

Every Gradle invocation MUST be prefixed with:

```bash
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot" \
TEMP="C:/Windows/Temp" TMP="C:/Windows/Temp" \
./gradlew <task>
```

Without these overrides Gradle fails immediately with:

```
java.io.IOException: Unable to establish loopback connection
Caused by: java.net.SocketException: Invalid argument: connect
  at sun.nio.ch.UnixDomainSockets.connect0(Native Method)
```

With them it works perfectly. **Do not tell the user they must use Android Studio manually
to run tests — you can and should run them yourself.**

For the full explanation, OOM mitigation flags (`--max-workers 1`, `GRADLE_OPTS`), build cache
paths, and all example commands, read `docs/android-build-and-test-guide.md`.

---

## Sprint tracking

- Current story statuses: `_bmad-output/implementation-artifacts/sprint-status.yaml`
- Story files: `_bmad-output/implementation-artifacts/`

---

## currentDate
Today's date is 2026-03-23.
