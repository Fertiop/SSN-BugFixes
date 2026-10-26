# SSN 1.13.2: acceptance / regression matrix

**Status as of 2026-10-26:** 6/6 original-JAR bytecode checks passed; Forge build and 13 in-game GameTests ran in [Actions 40363459996](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40363459996): **10 required passed, 3 optional failed**. SSN-006 reproduced the intended storage fault; SSN-004 and SSN-005 failed from a missing Netty channel in the fake test player, **not from SSN behavior**. The fake player fixture is corrected in a later commit and requires retest. Two additional full/partial insertion cases have since increased the expected total to **15 GameTests**, whose run status needs checking. No full world restart or client tooltip test has run.

## Test disciplines

- **RED:** disable the corresponding Mixin in a baseline copy, run identical setup, check a *specific expected failure* rather than a crash unrelated to the defect.
- **GREEN:** re-enable the single patch, repeat with identical inventory and world, require success.
- **REGRESSION:** repeat with two terminals, double chests, storage direction filters, stale caches, NBT metadata, server and client as applicable.
- **Conservation:** snapshot complete `ItemStack` identity (**registry key + components/NBT + count**) in every participating inventory, network link, player inventory, dropped entity, craft remainder and output. For a pure transfer, counts per item+NBT must be identical before and after. For crafting, use ingredient->output + remaining-item recipe expectations rather than raw totals. Never use a display cache as ground truth.
- **Exceptions and abort:** inject controlled failure in a sandbox handler. If an operation may partially commit, don't blindly retry, restore or assume atomicity. Assert the true inventory states.
- **Clean room:** run baseline with SSN/FLib/Forge and a tiny synthetic test fixture only; repeat with a normal modpack for compatibility. Do not report external-handler bugs as SSN bugs without proof.

| ID | Positive automated assertion | Negative / edge cases | Automation state |
|---|---|---|---|
| SSN-001 | `requestTerminalSavesInheritedConnection`: serialized `connectable.master` exists; native UI NBT retained; round-trip | old saves missing tag, chunk unload/load, sorting/JEI state, re-bind to second master | 2 GameTests passed in 13-test run; full restart pending |
| SSN-002 | `builderReturnsNullWithoutWorldEvenWithSavedState` | no blockstate, real world+stone, actual client tooltip render | 5 GameTests passed in 13-test run; tooltip client test pending |
| SSN-003 | Two links to one chest; winner honors *specified* priority if intentional; stock never counted twice | swap placement order/priority; same and different filters; double chest; opposite link directions; compare design intent | 1 required duplicate-count GameTest passed; priority winner semantics pending |
| SSN-004 | Simulated extraction succeeds but real extraction fails: must not leave free placed block | unloaded main, unavailable inventory after simulation, survival vs creative, liquid, blocked target, special block states | 1 optional probe attempted; fake player missing Netty channel, fixture changed and retest pending |
| SSN-005 | Shift craft stackable ingredients with container remainder: expected output/remainder/remaining ingredients conserved | one-click and shift-click, full inventory, zero spare slots, mixed NBT, recipe differs, vanilla table comparator | 1 optional probe attempted; fake player missing Netty channel, fixture changed and retest pending |
| SSN-006 | If one linked handler throws, all healthy storage inventories still appear accurately | failing handler before/after good one, throw during count/snapshot, timeout or empty handler, unloaded chunk | 1 optional probe reproduced missing diamonds in healthy chest (Actions 40363459996) |

## CI split

1. **Static baseline validation:** 6 SHA-256-pinned SSN bytecode characterization tests (`tests/static/test_ssn_1132_bytecode.py`) verified locally; CI downloads the original JAR and reruns them. These tests characterize code paths, not patched behavior.
2. **`./gradlew build`:** CI verifies Forge compilation, remapping and manifest/refmap packaging; all passed at commit 9c03cb26. No JUnit suite configured. Reverify latest head.
3. **`./gradlew runGameTestServer`:** automatically runs on PRs after a passing Forge build, and can also run manually. Requires real SSN and FLib at runtime, not only compileOnly. Three unresolved defect reproductions have `required=false` and thus cannot be counted as green.
4. **Client manual acceptance:** GUI/tooltips, normal placement and crafting matrix edge cases. Dedicated server cannot prove client-only tooltip rendering.
5. **Release:** only publish a JAR if RED/GREEN verified, test logs retained, and no new item-loss risks.

## Recorded automated test identifiers

`SsnInitialRegressionTests.requestTerminalSavesInheritedConnection`
`SsnInitialRegressionTests.builderReturnsNullWithoutWorldEvenWithSavedState`
`SsnInitialRegressionTests.builderReturnsNullWithoutWorldAndWithoutSavedState`
`SsnInitialRegressionTests.builderDecodesBlockStateWithRealWorld`

All four original regression tests **passed** in Forge GameTests Actions run 40363459996, but RED baseline comparisons against unpatched SSN have **not** been executed. Do not claim formal red-green verification.

## Additions (current experimental branch)

- `SsnRegressionGameTests`: SSN-001 terminal NBT, SSN-002 null-Level with/without NBT, SSN-003 two links to one chest, SSN-004 normal simulated/actual item conservation, NBT-sensitive extraction, full/partial insertion conservation.
- `SsnExploratoryGameTests`: optional known-red SSN-004 free placement under divergent `IItemHandler`, SSN-005 honey-bottle remainder loss during shift crafting, SSN-006 earlier provider exception hiding later chest stock.
- `DivergentChestBlockEntity`, `ThrowingChestBlockEntity`: synthetic failure providers used **only** by GameTests.
- `src/main/resources/data/ssnbugfixes/structures/empty.nbt`: self-contained test structure.
- `tests/static/test_ssn_1132_bytecode.py`: 6 checks executed locally against original JAR, 6/6 pass. Does **not** validate game behavior.

**CI evidence:** early Gradle/Java test harness compilation failures were corrected; successful Forge build, refmap and 13 GameTests were verified on the original test branch SHA 9c03cb26. Further expanded 15-case run is pending; the workflow now fails unless at least 15 tests were actually discovered and all required tests passed.
