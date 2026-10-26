# SSN 1.13.2: acceptance / regression matrix

**Status:** Only SSN-001 and SSN-002 have initial automated GameTest source. **All tests are NOT RUN**; nothing in this table proves a fix. Run only in a disposable world on a pinned Minecraft/Forge/SSN stack after authorization. An upstream report from another SSN release is not proof in 1.13.2.

## Test disciplines

- **RED:** disable the corresponding Mixin in a baseline copy, run identical setup, check a *specific expected failure* rather than a crash unrelated to the defect.
- **GREEN:** re-enable the single patch, repeat with identical inventory and world, require success.
- **REGRESSION:** repeat with two terminals, double chests, storage direction filters, stale caches, NBT metadata, server and client as applicable.
- **Conservation:** snapshot complete `ItemStack` identity (**registry key + components/NBT + count**) in every participating inventory, network link, player inventory, dropped entity, craft remainder and output. For a pure transfer, counts per item+NBT must be identical before and after. For crafting, use ingredient->output + remaining-item recipe expectations rather than raw totals. Never use a display cache as ground truth.
- **Exceptions and abort:** inject controlled failure in a sandbox handler. If an operation may partially commit, don't blindly retry, restore or assume atomicity. Assert the true inventory states.
- **Clean room:** run baseline with SSN/FLib/Forge and a tiny synthetic test fixture only; repeat with a normal modpack for compatibility. Do not report external-handler bugs as SSN bugs without proof.

| ID | Positive automated assertion | Negative / edge cases | Automation state |
|---|---|---|---|
| SSN-001 | `requestTerminalSavesInheritedConnection`: serialized `connectable.master` exists; native UI NBT retained; round-trip | old saves missing tag, chunk unload/load, sorting/JEI state, re-bind to second master | 1 GameTest authored, not run; full restart pending |
| SSN-002 | `builderReturnsNullWithoutWorldEvenWithSavedState` | no blockstate, real world+stone, actual client tooltip render | 3 GameTests authored, not run; tooltip client test pending |
| SSN-003 | Two links to one chest; winner honors *specified* priority if intentional; stock never counted twice | swap placement order/priority; same and different filters; double chest; opposite link directions; compare design intent | Integration fixture and specification pending |
| SSN-004 | Simulated extraction succeeds but real extraction fails: must not leave free placed block | unloaded main, unavailable inventory after simulation, survival vs creative, liquid, blocked target, special block states | Synthetic capability fixture and integration test pending |
| SSN-005 | Shift craft stackable ingredients with container remainder: expected output/remainder/remaining ingredients conserved | one-click and shift-click, full inventory, zero spare slots, mixed NBT, recipe differs, vanilla table comparator | Version 1.13.2 reproduction and recipe fixture pending |
| SSN-006 | If one linked handler throws, all healthy storage inventories still appear accurately | failing handler before/after good one, throw during count/snapshot, timeout or empty handler, unloaded chunk | Synthetic fault-injecting handler and fixture pending |

## CI split

1. **Static validation:** validate JSON and source presence, no SSN JAR accidentally committed, check license and version metadata. Fast and safe on every push. No Minecraft startup.
2. **`./gradlew compileJava` and `./gradlew test`:** Gradle/JVM verification after dependencies are pinned and build authorized. JUnit isn't currently configured; do not claim JUnit coverage.
3. **`./gradlew runGameTestServer`:** launches Minecraft and executes Forge GameTests. Must install actual SSN **and FLib** in the test runtime, not only `compileOnly`. Without them the run is invalid.
4. **Client manual acceptance:** GUI/tooltips, normal placement and crafting matrix edge cases. Dedicated server cannot prove client-only tooltip rendering.
5. **Release:** only publish a JAR if RED/GREEN verified, test logs retained, and no new item-loss risks.

## Recorded automated test identifiers

`SsnInitialRegressionTests.requestTerminalSavesInheritedConnection`
`SsnInitialRegressionTests.builderReturnsNullWithoutWorldEvenWithSavedState`
`SsnInitialRegressionTests.builderReturnsNullWithoutWorldAndWithoutSavedState`
`SsnInitialRegressionTests.builderDecodesBlockStateWithRealWorld`

All results: **NOT RUN** as of 2026-10-26. A passing Java compilation would also not prove the original RED case.
