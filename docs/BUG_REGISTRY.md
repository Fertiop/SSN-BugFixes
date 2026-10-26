# SSN 1.13.2 — error and investigation registry

**Scope:** Minecraft 1.20.1 / Forge 47.x / SSN 1.13.2. **Date opened:** 2026-10-26.

Important: these are SSN's own behavior paths, **not Factotum ↔ SSN compatibility reports**. No runtime tests or local rebuild have been performed in this repository. Do not mark as fixed without evidence.

| ID | Area | Status | Risk | Next check |
|---|---|---|---|---|
| SSN-001 | TileRequest serialisation | fixed-unverified (source patch) | Stored network link may be missing after reload | Save/reload with and without network refresh |
| SSN-002 | ItemBuilder tooltip | fixed-unverified (source patch) | NPE when tooltip receives a null Level | Validate null-argument contract and reproduction |
| SSN-003 | Duplicate-target cable selection | hypothesis / code path confirmed | Priority/filter of second cable can be ignored | Determine intended dedup semantics; test differing priorities |
| SSN-004 | Builder remote block placement | hypothesis / code path confirmed | Block placed even if final item extraction fails | Reproduce simulate/execute divergence |
| SSN-005 | Crafting ingredient remainder | upstream report, unverified in 1.13.2 | Possible item loss on shift-crafting | Reproduce on 1.13.2; compare upstream versions |
| SSN-006 | Network list enumeration exception | static-confirmed code path, impact unmeasured | Failure of one storage stops enumeration of later storages | Test one faulty handler among valid handlers |

## SSN-001 — TileRequest saveAdditional does not save base connectable capability
- **Patch candidate:** `src/main/java/io/github/fertiop/ssnbugfixes/mixin/TileRequestSaveMixin.java`; `@Inject` at `saveAdditional` TAIL invokes `super.saveAdditional(tag)`.
- **Candidate status:** `fixed-unverified`; **no build, Minecraft execution or red-green regression validation**. Verify remapping of the inherited vanilla method to `m_183515_` in production; see `docs/TEST_CASES.md`.

- **Location:** [TileRequest.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/block/request/TileRequest.java) / [TileConnectable.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/block/TileConnectable.java).
- **Observation:** `TileRequest.saveAdditional(CompoundTag)` writes sorting and UI values without `super.saveAdditional(compound)`; parent serializes `connectable` (including link to main controller). Child `load` does call `super.load`.
- **Uncertainty:** The network may reconstruct the association on refresh; verify persistence consequences in game before choosing fix.
- **Regression checks:** saved NBT contains inherited data; reload network without unexpected rebind or broken search.

## SSN-002 — ItemBuilder tooltip does not handle nullable Level
- **Patch candidate:** `src/main/java/io/github/fertiop/ssnbugfixes/mixin/ItemBuilderNullLevelMixin.java`; null `Level` returns null; non-null delegates to SSN.
- **Candidate status:** `fixed-unverified`; **no build or test execution**. See `docs/TEST_CASES.md`.

- **Location:** [ItemBuilder.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/item/ItemBuilder.java) / [upstream issue #578](https://github.com/Lothrazar/Storage-Network/issues/578).
- **Observation:** `appendHoverText` passes its world argument to `getBlockState`; `getBlockState` uses `level.holderLookup(...)` when the stack has stored block state.
- **Trigger:** null Level *and* builder stack containing a `blockstate` tag. Not necessarily reached in vanilla GUI.
- **Regression checks:** tooltip with null Level and with a real Level, including stack without saved block state.

## SSN-003 — Same-inventory cable dedup precedes priority selection
- **Location:** [NetworkModule.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/block/main/NetworkModule.java).
- **Observation:** `getConnectableStorage` sorts cable positions, dedups same `canonicalTargetKey`, adds only the first to `HashSet`; `getSortedConnectableStorage` applies priority **after** dedup.
- **Disproof to attempt:** dedup is intentional to prevent double counting or duplicate extraction. Confirm expected winner selection before calling it an error.
- **Regression checks:** two link cables with different priorities/directions/filters pointing to one chest and either half of a double chest.

## SSN-004 — Builder remote places before consuming material
- **Location:** [ItemBuilder.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/item/ItemBuilder.java).
- **Observation:** `request(..., true)` checks available item, `placeStateSafe` places a block, and only then `request(..., false)` executes; result is ignored.
- **Uncertainty:** A divergence between simulation and execution must be demonstrated. Do not assume unconditional free placement.
- **Regression checks:** capability that permits simulate but fails real extraction; concurrent mutation; block placement failure.

## SSN-005 — Crafting remainder on stackable ingredients
- **Source:** [upstream issue #576](https://github.com/Lothrazar/Storage-Network/issues/576) was reported against SSN 1.12.2, not proven in 1.13.2.
- **Next checks:** stackable recipe remainder; shift-click; vanilla comparator; find any intervening fix.
- **Status:** external unverified report. Not a confirmed 1.13.2 defect.

## SSN-006 — getStacks aborts enumeration on one failing provider
- **Location:** [NetworkModule.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/block/main/NetworkModule.java).
- **Observation:** `try/catch` surrounds entire loop over storages in `getStacks(boolean)`; one provider exception skips all later providers in that invocation.
- **Uncertainty:** Frequency and user-visible impact depend on external storage handlers. Confirm ordering and symptom before prioritizing.
- **Regression checks:** faulty handler amid valid connected chests; no missing unrelated counts.

## Recording an actual patch
Each entry must also record: exact SSN source commit and release JAR hash; environment; minimum steps; root cause; rejected hypotheses; added fix files; before/after behavior; build and game-test result; rollback notes.

## 2026-10-26 source-patch investigation provenance
- Provided SSN JAR SHA-256: `1db6f5595e5edc255db6800de9e0e1aa8e01e10d79d1784d549d70eb2cf53012` (matched by fresh byte inspection).
- Upstream code baseline: `Lothrazar/Storage-Network` `trunk/1.20.1@7f3573757b7585d9f98fac5f725fa1a9eb5db276`.
- Bytecode confirmed: `TileRequest.m_183515_` lacks parent `TileConnectable.m_183515_` call, `ItemBuilder.getBlockState` dereferences null `Level` when item has `blockstate`.
- Patches are isolated on branch `fix/ssn-001-002-source-patches`; do not merge/release before verification.
