# SSN-BugFixes testing guide

## What exists today

- Four Java Forge GameTests using actual SSN methods in `SsnInitialRegressionTests`, covering SSN-001 and SSN-002.
- Test cases for all six registry entries in `docs/TEST_MATRIX.md`.
- A **manually triggered** GitHub Actions workflow in `.github/workflows/gametest-manual.yml`. It will not execute on push or PR.
- A separate client GUI session and a multi-world persistence test are still needed.
- Tests are **NOT RUN**; code is **NOT BUILD VERIFIED**.

## Environment

Java 17, Forge 47.3.0, Minecraft 1.20.1, real SSN 1.13.2 (Modrinth `cVIr8Vz1/UmSTgKWE`) and FLib 0.0.16 (`FAUSd09K/CDihtEoy`). The Gradle development runtime pulls both mods; they are not redistributed in our own JAR.

## Executing only when authorized

```sh
./gradlew compileJava
./gradlew runGameTestServer
```

Or go to GitHub Actions -> **SSN 1.13.2 GameTests (manual)** -> **Run workflow**, selecting the experimental branch. The workflow will start a full game test server and use a disposable environment. An exit code of 0 must be corroborated by the actual GameTest logs; if zero tests were discovered, **that is a failure**.

## Establishing RED / GREEN

Make an isolated copy without the SSN-BugFixes mixin config enabled while leaving vanilla SSN installed. Baseline cases must fail for the expected reason, not because SSN fails to load. Then restore the Mixin config and require the same cases to pass. Confirm the actual refmap and injection application. The checked-in patch is not automatically correct because its target method appears in source.

## Testing the remaining issues

SSN-003 through SSN-006 require fixture-driven tests that involve multiple real tile entities, synthetic capability handlers or specific container crafting behavior. We deliberately **did not create fake unit tests** that assert a test-only implementation rather than the original SSN code. Their red/green tests will be implemented after the respective defects are demonstrated in 1.13.2.

See [test matrix](TEST_MATRIX.md) for precise scenarios and invariants.
