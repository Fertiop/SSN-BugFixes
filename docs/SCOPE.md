# Scope and design

## Target
- `Minecraft 1.20.1`
- `Forge 47.3.0+` within the `47.x` series
- `Simple Storage Network 1.13.2` only, mod ID `storagenetwork`
- `Java 17`
- FLib version supported by the installed SSN distribution

## Architecture
Standalone Forge mod `ssnbugfixes` installed **alongside** official SSN. The mod must declare a mandatory SSN dependency and refuse mismatched SSN versions. Runtime behavior patches, when approved and implemented, should be narrowly targeted, preferably by dedicated Mixin per confirmed defect. One source file per patch area; avoid copying SSN's implementation wholesale.

## Project lifecycle
1. Confirm defect against SSN 1.13.2 release binary; search existing issues and source history.
2. Record exact trigger and minimal reproduction.
3. Implement a narrowly scoped and reversible fix.
4. Prove zero item loss or duplication; cover reload, client/server and error paths.
5. Build and test only when authorized; record exact binaries, test reports and versions.
6. Publish a release JAR only after verification.

## Out of scope
- Factotum compatibility, permissions or tools.
- Broad rewrites, feature requests, or unrelated optimizations.
- Repackaging or replacing the upstream SSN JAR.
- Treating reports from older SSN versions as proof of 1.13.2 failure.

## Current limitation
The initial repository setup provides infrastructure, not finished SSN patches. A Forge starter is **not** a bug fix until a patch and its tests are verified.
