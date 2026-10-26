# SSN-BugFixes testing guide

## What exists today

- Four original Java GameTests in `SsnInitialRegressionTests` plus expanded `SsnRegressionGameTests` (SSN-001/002/003/normal extraction+NBT) and optional expected-red `SsnExploratoryGameTests` (SSN-004/005/006).
- Test cases for all six registry entries in `docs/TEST_MATRIX.md`.
- Two workflows: `.github/workflows/ssn-tests.yml` runs 6 bytecode checks and a Forge build on PR, then invokes GameTests after a successful build; `.github/workflows/gametest-manual.yml` supports isolated manual GameTests.
- A separate client GUI session and a multi-world persistence test are still needed.
- **Six original-JAR static characterization tests PASSED locally and in GitHub Actions.** Those tests characterize the *unpatched* SSN binary, not a successful fix. Forge build and GameTest verification are **not confirmed**; initial CI runs exposed a bad Gradle config and then Java test type errors, which were corrected in later commits.

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

SSN-003 has an active double-link dedup GameTest. SSN-004/005/006 now have optional GameTests against real SSN logic and custom *test-only* faulty inventory handlers: builder placement after declined extraction; stackable honey bottle crafting remainders; faulty provider hiding healthy inventory. These are source-only, **not yet executed**. Do not call them proven until the correct failing behavior is observed in Minecraft. Preserve all six JAR characterization checks as separate baseline evidence.

See [test matrix](TEST_MATRIX.md) for precise scenarios and invariants.

## Reproducible upstream bytecode checks

```sh
SSN_JAR=/path/to/SimpleStorageNetwork-1.20.1-1.13.2.jar python -m unittest discover -s tests/static -p 'test_*.py' -v
```

Actual six-check result against supplied SSN JAR: **6 passed** (2026-10-26). GitHub Actions upstream-bytecode-contracts job also passed. This only confirms investigated upstream code paths.

## CI references

- [Draft PR #1](https://github.com/Fertiop/SSN-BugFixes/pull/1) contains experimental fixes and test code.
- Build must succeed, mixin refmap must be packaged, and real GameTest checks must run **with nonzero test discovery** before merging; optional failures for unresolved SSN-004/005/006 are expected-red, not green.
