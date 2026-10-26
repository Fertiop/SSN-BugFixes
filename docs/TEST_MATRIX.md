# SSN 1.13.2: acceptance / regression matrix

**Status:** Six upstream JAR bytecode characterization checks executed locally (6/6 PASS); behavioral GameTests written for SSN-001–006 but **none executed yet**. SSN-001/002/003 and basic extraction checks are required; SSN-004/005/006 are exploratory `required=false` (known-red). These facts do NOT prove patches or unpatched hypotheses. Run only in a disposable world on a pinned Minecraft/Forge/SSN stack after authorization. An upstream report from another SSN release is not proof in 1.13.2.

## Test disciplines

- **RED:** disable the corresponding Mixin in a baseline copy, run identical setup, check a *specific expected failure* rather than a crash unrelated to the defect.
- **GREEN:** re-enable the single patch, repeat with identical inventory and world, require success.
- **REGRESSION:** repeat with two terminals, double chests, storage direction filters, stale caches, NBT metadata, server and client as applicable.
- **Conservation:** snapshot complete `ItemStack` identity (**registry key + components/NBT + count**) in every participating inventory, network link, player inventory, dropped entity, craft remainder and output. For a pure transfer, counts per item+NBT must be identical before and after. For crafting, use ingredient->output + remaining-item recipe expectations rather than raw totals. Never use a display cache as ground truth.
- **Exceptions and abort:** inject controlled failure in a sandbox handler. If an operation may partially commit, don't blindly retry, restore or assume atomicity. Assert the true inventory states.
- **Clean room:** run baseline with SSN/FLib/Forge and a tiny synthetic test fixture only; repeat with a normal modpack for compatibility. Do not report external-handler bugs as SSN bugs without proof.

| ID | Positive automated assertion | Negative / edge cases | Automation state |
|---|---|---|---|
| SSN-001 | `requestTerminalSavesInheritedConnection`: serialized `connectable.master` exists; native UI NBT retained; round-trip | old saves missing tag, chunk unload/load, sorting/JEI state, re-bind to second master | 2 GameTests authored, not run; full restart pending |
| SSN-002 | `builderReturnsNullWithoutWorldEvenWithSavedState` | no blockstate, real world+stone, actual client tooltip render | 5 GameTests authored (3 in earlier suite, 2 expanded), not run; tooltip client test pending |
| SSN-003 | Two links to one chest; winner honors *specified* priority if intentional; stock never counted twice | swap placement order/priority; same and different filters; double chest; opposite link directions; compare design intent | 1 required duplicate-count GameTest authored; priority winner semantics pending |
| SSN-004 | Simulated extraction succeeds but real extraction fails: must not leave free placed block | unloaded main, unavailable inventory after simulation, survival vs creative, liquid, blocked target, special block states | 1 optional failure-injection GameTest authored, not run |
| SSN-005 | Shift craft stackable ingredients with container remainder: expected output/remainder/remaining ingredients conserved | one-click and shift-click, full inventory, zero spare slots, mixed NBT, recipe differs, vanilla table comparator | 1 optional honey-bottle shift-crafting GameTest authored, not run |
| SSN-006 | If one linked handler throws, all healthy storage inventories still appear accurately | failing handler before/after good one, throw during count/snapshot, timeout or empty handler, unloaded chunk | 1 optional throwing-handler GameTest authored, not run |

## CI split

1. **Static baseline validation:** 6 SHA-256-pinned SSN bytecode characterization tests (`tests/static/test_ssn_1132_bytecode.py`) verified locally; CI downloads the original JAR and reruns them. These tests characterize code paths, not patched behavior.
2. **`./gradlew build`:** CI attempts Forge compilation, remapping and manifest/refmap packaging; no JUnit suite is configured. Treat status as not verified until Actions succeeds.
3. **`./gradlew runGameTestServer`:** opt-in GitHub Actions job. Requires real SSN and FLib at runtime, not only compileOnly. Three unresolved defect reproductions have `required=false` and thus cannot be counted as green.
4. **Client manual acceptance:** GUI/tooltips, normal placement and crafting matrix edge cases. Dedicated server cannot prove client-only tooltip rendering.
5. **Release:** only publish a JAR if RED/GREEN verified, test logs retained, and no new item-loss risks.

## Recorded automated test identifiers

`SsnInitialRegressionTests.requestTerminalSavesInheritedConnection`
`SsnInitialRegressionTests.builderReturnsNullWithoutWorldEvenWithSavedState`
`SsnInitialRegressionTests.builderReturnsNullWithoutWorldAndWithoutSavedState`
`SsnInitialRegressionTests.builderDecodesBlockStateWithRealWorld`

All results: **NOT RUN** as of 2026-10-26. A passing Java compilation would also not prove the original RED case.

## Additions (current experimental branch)

- `SsnRegressionGameTests`: SSN-001 terminal NBT, SSN-002 null-Level with/without NBT, SSN-003 two links to one chest, SSN-004 normal simulated/actual item conservation.
- `SsnExploratoryGameTests`: optional known-red SSN-004 free placement under divergent `IItemHandler`, SSN-005 honey-bottle remainder loss during shift crafting, SSN-006 earlier provider exception hiding later chest stock.
- `DivergentChestBlockEntity`, `ThrowingChestBlockEntity`: synthetic failure providers used **only** by GameTests.
- `src/main/resources/data/ssnbugfixes/structures/empty.nbt`: self-contained test structure.
- `tests/static/test_ssn_1132_bytecode.py`: 6 checks executed locally against original JAR, 6/6 pass. Does **not** validate game behavior.

**CI evidence:** first build attempt failed during Gradle configuration due unsupported `setForceExit false` in `gameTestServer`; corrected in subsequent commit. Full recheck remains pending.
