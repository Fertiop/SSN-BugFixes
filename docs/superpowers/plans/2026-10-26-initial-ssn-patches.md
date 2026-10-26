# SSN-001/002 initial patches implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restore inherited TileRequest save data and handle null-world builder item lookup in SSN 1.13.2.

**Architecture:** Forge companion mod with one Mixin config and two isolated injections; SSN is a compile-only dependency, not repackaged.

**Tech Stack:** Minecraft 1.20.1, Forge 47.3.0, Java 17, Mixin 0.8.5, Gradle, official Modrinth SSN 1.13.2 artifact.

**Spec:** `docs/superpowers/specs/2026-10-26-initial-ssn-patches-design.md`

## Global Constraints
- Do not build or run tests until separately authorized.
- Never mark untested patches as fixed. Keep changes in isolated branch until verified.
- Pin SSN dependency to `maven.modrinth:cVIr8Vz1:UmSTgKWE`; apply runtime version restriction `[1.13.2,1.13.3)`.
- No original SSN JAR or third-party libraries committed.

## Review Focus
- SaveAdditional inherited serialization: both UI and connectable data persist.
- Restart with old save missing connectable NBT: no loader crash.
- A null world and a block-state tag returns null rather than an exception.
- A null world with no block-state tag still returns null.
- A real world + saved block state yields original BlockState.

---

### Task 1: Mixin infrastructure and version pin
**Files:** `build.gradle`, `src/main/resources/ssnbugfixes.mixins.json`

- [x] Confirm original SSN release JAR and source methods via bytecode and source.
- [ ] Configure MixinGradle, annotation processor, refmap, `MixinConfigs`.
- [ ] Add compile-only Modrinth SSN dependency; retain `mods.toml` version constraint.
- [ ] Inspect generated manifest and refmap (blocked: build not authorized).
- [ ] Commit isolated work.

### Task 2: SSN-001 TileRequest save
**Files:** `src/main/java/io/github/fertiop/ssnbugfixes/mixin/TileRequestSaveMixin.java`

- [x] Inspect original bytecode: `m_183515_` omits parent call.
- [ ] Add injection that invokes the parent serialization at TAIL.
- [ ] Test NBT saved and restored on reload (blocked: game tests not authorized).
- [ ] Document result, limitations, and commit.

### Task 3: SSN-002 ItemBuilder null world
**Files:** `src/main/java/io/github/fertiop/ssnbugfixes/mixin/ItemBuilderNullLevelMixin.java`

- [x] Inspect original bytecode: null world dereferenced with saved `blockstate`.
- [ ] Add narrow HEAD guard; do not modify behavior for non-null world.
- [ ] Test both saved and absent block-state tag (blocked: game tests not authorized).
- [ ] Document result, limitations, and commit.

### Task 4: Audit integration
**Files:** `docs/BUG_REGISTRY.md`, `docs/TEST_CASES.md`, `README.md`

- [ ] Update registry to `fixed-unverified`, not `verified-fixed`.
- [ ] Document exact manual and automated game regression cases.
- [ ] Check actual committed contents and diff without running tests.
- [ ] Open a draft PR and defer merge until build and game verification.
