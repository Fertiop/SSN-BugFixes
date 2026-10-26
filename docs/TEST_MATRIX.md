# SSN 1.13.2: acceptance / regression matrix

**Last verified game run:** [Actions 40365724004](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40365724004), commit `f92be520`: 15 GameTests ran, 12 required passed, three optional failed **for their intended defect assertions**, not from broken fake player. SSN-004 free block placement and SSN-006 missing healthy chest stock reproduced with test-only divergent/throwing capabilities. SSN-005 initially showed lost items, **but the test incorrectly used inherited `NetworkCraftingInventory.getItems()`**; corrected to enumerate `getItem(slot)` and account for dropped items. A control assertion for recipe remainders was added. **SSN-005 reproduced on corrected test** in [Actions 40378602235](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40378602235): 8 honey bottles -> 1 block + 4 glass + 0 unused honey, no nearby drops; four honey bottles unaccounted for. Further vanilla-control and menu-close comparisons are still useful. Optional SSN-003 priority-and-filter probe **failed for the expected observation** in [Actions 40378719501](https://github.com/Fertiop/SSN-BugFixes/actions/runs/40378719501): 7 diamonds present, high-priority link unfiltered, low-priority link filters them, 0 visible. Confirms configured priority did not win in this same-inventory case; intended policy remains under review. 16 cases executed (12 required green, 4 optional expected-red). New-head CI remains under verification. Six JAR bytecode baseline checks pass and Forge build was verified, but live-world restart/client UI acceptance is not yet done.



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
| SSN-003 | Two links to one chest; winner honors *specified* priority if intentional; stock never counted twice | swap placement order/priority; same and different filters; double chest; opposite link directions; compare design intent | 1 required dedup check passed; 1 optional priority/filter conflict reproduced (0 vs 7 expected) in actual GameTest |
| SSN-004 | Simulated extraction succeeds but real extraction fails: must not leave free placed block | unloaded main, unavailable inventory after simulation, survival vs creative, liquid, blocked target, special block states | 1 optional GameTest reached the fault assertion: SSN-004 reproduced; additional scenarios remain |
| SSN-005 | Shift craft stackable ingredients with container remainder: expected output/remainder/remaining ingredients conserved | one-click and shift-click, full inventory, zero spare slots, mixed NBT, recipe differs, vanilla table comparator | 1 optional GameTest reached the fault assertion: SSN-004 reproduced; additional scenarios remain |
| SSN-006 | If one linked handler throws, all healthy storage inventories still appear accurately | failing handler before/after good one, throw during count/snapshot, timeout or empty handler, unloaded chunk | 1 optional GameTest reproduced missing healthy chest items (Actions 40365724004) |

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
- `SsnExploratoryGameTests`: optional known-red SSN-003 priority vs duplicate filter, SSN-004 free placement under divergent `IItemHandler`, SSN-005 honey-bottle shift-crafting remainder (with explicit control and drop accounting), SSN-006 earlier provider exception hiding later chest stock.
- `DivergentChestBlockEntity`, `ThrowingChestBlockEntity`: synthetic failure providers used **only** by GameTests.
- `src/main/resources/data/ssnbugfixes/structures/empty.nbt`: self-contained test structure.
- `tests/static/test_ssn_1132_bytecode.py`: 6 checks executed locally against original JAR, 6/6 pass. Does **not** validate game behavior.

**CI evidence:** early Gradle/Java test harness compilation failures were corrected; successful Forge build, refmap and 13 GameTests were verified on the original test branch SHA 9c03cb26. Further expanded 15-case run is pending; the workflow now fails unless at least 15 tests were actually discovered and all required tests passed.

## Test packaging policy

`build.gradle` excludes `io/github/fertiop/ssnbugfixes/gametest/**` and `data/ssnbugfixes/structures/empty.nbt` from the distributable JAR. Development GameTest runs still see these classes. CI inspects the built JAR and fails if any test-only bytecode or fixture leaks. This prevents intentional throwing inventory providers from becoming part of the shipped mod.

## Interpreting the 16-case run

Four optional exploratory probes can fail while the CI game-server job succeeds. CI must require >=16 registered tests, assert a completed GameTest summary and absence of required failures; **optional red is evidence of an unresolved defect, not of a repaired bug**. Stop deployment if either SSN-001 or SSN-002 required check fails. For SSN-003, if the competing-priority optional test fails, confirm intended user-visible priority semantics independently before adding a patch.
