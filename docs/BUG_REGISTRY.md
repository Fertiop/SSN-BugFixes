# SSN 1.13.2 — error and investigation registry

**Scope:** Minecraft 1.20.1 / Forge 47.x / SSN 1.13.2. **Date opened:** 2026-10-26.

Important: these are SSN's own behavior paths, **not Factotum ↔ SSN compatibility reports**. Forge CI builds and in-game GameTests have executed; only a subset are green. Do **not** treat a passing build/isolated GameTest as proof of full world-restart or client GUI correctness.

| ID | Area | Status | Risk | Next check |
|---|---|---|---|---|
| SSN-001 | TileRequest serialisation | game-tested (save/load NBT), disk reload pending | Stored network link may be missing after reload | Save/reload with and without network refresh |
| SSN-002 | ItemBuilder tooltip | game-tested null Level, client tooltip pending | NPE when tooltip receives a null Level | Validate null-argument contract and reproduction |
| SSN-003 | Duplicate-target cable selection | reproduced (priority+filter conflict, game; intent review pending) | Priority/filter of second cable can be ignored | Determine intended dedup semantics; test differing priorities |
| SSN-004 | Builder remote block placement | reproduced (divergent handler, game) | Block placed even if final item extraction fails | Reproduce simulate/execute divergence |
| SSN-005 | Crafting ingredient remainder | reproduced (8 honey bottles, GameTest) | Possible item loss on shift-crafting | Reproduce on 1.13.2; compare upstream versions |
| SSN-006 | Network list enumeration exception | reproduced (synthetic handler, game) | Failure of one storage stops enumeration of later storages | Test one faulty handler among valid handlers |

## SSN-001 — TileRequest saveAdditional does not save base connectable capability
- **Patch candidate:** `src/main/java/io/github/fertiop/ssnbugfixes/mixin/TileRequestSaveMixin.java`; `@Inject` at `saveAdditional` TAIL invokes `super.saveAdditional(tag)`.
- **Candidate status:** `fixed-unverified`; **Forge build and real GameTest save/load NBT assertions passed** in Actions 40365724004. However the original-red/patch-green comparison and **full disk save/reopen** have not run; see `docs/TEST_CASES.md`.

- **Location:** [TileRequest.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/block/request/TileRequest.java) / [TileConnectable.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/block/TileConnectable.java).
- **Observation:** `TileRequest.saveAdditional(CompoundTag)` writes sorting and UI values without `super.saveAdditional(compound)`; parent serializes `connectable` (including link to main controller). Child `load` does call `super.load`.
- **Uncertainty:** The network may reconstruct the association on refresh; verify persistence consequences in game before choosing fix.
- **Regression checks:** saved NBT contains inherited data; reload network without unexpected rebind or broken search.

## SSN-002 — ItemBuilder tooltip does not handle nullable Level
- **Patch candidate:** `src/main/java/io/github/fertiop/ssnbugfixes/mixin/ItemBuilderNullLevelMixin.java`; null `Level` returns null; non-null delegates to SSN.
- **Candidate status:** `fixed-unverified`; **Forge build and real GameTest checks passed** with a null Level (with and without stored blockstate), and normal Level behavior was preserved. Actual client-side tooltip rendering and original-red/patch-green comparison remain pending. See `docs/TEST_CASES.md`.

- **Location:** [ItemBuilder.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/item/ItemBuilder.java) / [upstream issue #578](https://github.com/Lothrazar/Storage-Network/issues/578).
- **Observation:** `appendHoverText` passes its world argument to `getBlockState`; `getBlockState` uses `level.holderLookup(...)` when the stack has stored block state.
- **Trigger:** null Level *and* builder stack containing a `blockstate` tag. Not necessarily reached in vanilla GUI.
- **Regression checks:** tooltip with null Level and with a real Level, including stack without saved block state.

## SSN-003 — Same-inventory cable dedup precedes priority selection
- **Coverage:** required GameTest confirms a chest with two SSN links is counted once (17, not 34). Optional test `ssn_003_higher_priority_duplicate_link_wins_filter` now checks a higher-priority unfiltered link versus lower-priority filtered link targeting the same chest. [Actions 40378719501](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40378719501) executed 16 GameTests and **reproduced a conflicting-priority outcome:** 7 real diamonds, higher-priority unfiltered link, lower-priority filtered link, **0 diamonds visible**. This supports the code-path observation: same-chest links deduplicated before priority sorting. Intended behavior when two storage links share one chest still requires explicit policy review; keep the double-count protection.

- **Location:** [NetworkModule.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/block/main/NetworkModule.java).
- **Observation:** `getConnectableStorage` sorts cable positions, dedups same `canonicalTargetKey`, adds only the first to `HashSet`; `getSortedConnectableStorage` applies priority **after** dedup.
- **Disproof to attempt:** dedup is intentional to prevent double counting or duplicate extraction. Confirm expected winner selection before calling it an error.
- **Regression checks:** two link cables with different priorities/directions/filters pointing to one chest and either half of a double chest.

## SSN-004 — Builder remote places before consuming material
- **Runtime reproduction (2026-10-26):** in [Forge Actions 40365724004](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40365724004) with SSN 1.13.2, an intentionally divergent `IItemHandler` simulated a stone item but refused real extraction. `ItemBuilder.useOn` nevertheless placed a stone block; optional GameTest failure was the expected behavioral assertion: `SSN-004: block appeared although real network extraction returned EMPTY`. This proves the conditional non-atomic path, not a typical vanilla inventory fault.
- **Patch:** none. Do not fix by unguarded retry or block rollback until effects of block placement and partial inventory operations are analyzed.

- **Location:** [ItemBuilder.java](https://github.com/Lothrazar/Storage-Network/blob/trunk/1.20.1/src/main/java/com/lothrazar/storagenetwork/item/ItemBuilder.java).
- **Observation:** `request(..., true)` checks available item, `placeStateSafe` places a block, and only then `request(..., false)` executes; result is ignored.
- **Uncertainty:** A divergence between simulation and execution must be demonstrated. Do not assume unconditional free placement.
- **Regression checks:** capability that permits simulate but fails real extraction; concurrent mutation; block placement failure.

## SSN-005 — Crafting remainder on stackable ingredients
- **Testing correction:** first live test showed missing honey/glass after shift craft, but it counted `NetworkCraftingInventory.getItems()`. That inherited method reads a different backing list from the actual SSN crafting grid. The test now counts `getItem(slot)` for every real slot and dropped ItemEntities; it also asserts that the vanilla recipe generates exactly four glass bottle remainders. **The first observation alone was insufficient; corrected in-game test then reproduced missing ingredients.** Run [40378602235](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40378602235) executed the corrected slot-by-slot and dropped-entity count: input 8 honey bottles, result 1 honey block, 4 glass bottles, **0 remaining honey bottles** in crafting grid/player inventory or nearby dropped stacks. 4 expected unused honey bottles were not recovered. This matches the upstream `craftShift` branch that replaces a *stack* of consumed container items with a single crafting remainder. Fix is not implemented. Test initially had a `getItems()` counting bug; this evidence comes after correcting it.

- **Source:** [upstream issue #576](https://github.com/Lothrazar/Storage-Network/issues/576) was reported against SSN 1.12.2, not proven in 1.13.2.
- **Next checks:** stackable recipe remainder; shift-click; vanilla comparator; find any intervening fix.
- **Status:** external unverified report. Not a confirmed 1.13.2 defect.

## SSN-006 — getStacks aborts enumeration on one failing provider
- **Runtime reproduction (2026-10-26):** GitHub Actions Forge GameTest against SSN 1.13.2, `SsnExploratoryGameTests.ssn_006_failure_in_one_provider_keeps_other_items_visible`, ran inside Minecraft and failed with `SSN-006: a throwing provider hid 7 diamonds in an unrelated healthy chest`. The injected broken IItemHandler deliberately threw `IllegalStateException: Intentional SSN-006 provider fault`. [Actions run 40363459996](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40363459996). This confirms the **conditional** fault isolation defect, not its frequency with normal handlers.
- **Patch status:** No fix for SSN-006 yet. Test is optional expected-red; turn required-green only after an implemented, verified fix.

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

## Regression suite evidence (2026-10-26)

- Pinned original SSN JAR, 6/6 bytecode characterization checks passed.
- Forge build + refmap/JAR manifest checks passed in [Actions 40363459996](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40363459996).
- Real Forge GameTest run in same workflow discovered 13 tests: **10 required passed; 3 optional failed**. SSN-006 failed for the expected defective-behavior assertion, while SSN-004 and SSN-005 failed due missing Netty channel in the fake player, a test harness problem and **not evidence of the SSN defects**. Fake player channel setup was subsequently revised, requires CI retest.
- New full/partial insertion and NBT conservation tests added after this run; their results must be checked separately on the latest branch.
- This evidence does **not** prove SSN-003 behavior about priorities or an SSN-005 item-loss bug in SSN 1.13.2.

## Current experimental test evidence

- [Actions 40365724004](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40365724004): `build`, Mixin manifest/refmap, 6 static baseline checks and **12 required / 15 total Forge GameTests** passed at the framework level. Three optional failures: SSN-004 and SSN-006 **reproduced their intended behavior**; SSN-005 had a known bad counting method in its test fixture.
- A corrected SSN-005 harness, an optional SSN-003 priority+filter probe, and CI gate for >=16 discovered GameTests were added afterwards. **Latest-head test outcome must be checked before merging**.
- Test-only synthetic chest handlers and the `empty.nbt` structure are now excluded from the production JAR. The build CI validates that exclusion.

### SSN-005 reproduction log (corrected test, 2026-10-26)

[Actions 40378602235](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40378602235), Forge GameTest `ssn_005_stackable_honey_bottle_remainder_conservation`: `8 honey bottles -> 1 blocks, 0 unconsumed honey (0 in grid), 4 glass (4 in grid), 0 dropped stacks`; required in-game tests passed. The vanilla honey-block recipe requires 4 honey bottles per output, so 4 unused bottles should remain after producing one block. Verify also after closing UI, in offhand and normal player inventory, and with a controlled vanilla crafting benchmark before making a public bug claim.
