# Initial SSN 1.13.2 bug-fix design — 2026-10-26

## Intent
Deliver the first two *source-level* patches to Simple Storage Network 1.13.2 in the independent Forge companion mod. The owner has approved starting code development; **build and game test execution require separate authorization**.

## Source baseline
- Upstream source: `Lothrazar/Storage-Network` `trunk/1.20.1`, commit `7f3573757b7585d9f98fac5f725fa1a9eb5db276`.
- Actual provided JAR: `SimpleStorageNetwork-1.20.1-1.13.2.jar`, SHA-256 `1db6f5595e5edc255db6800de9e0e1aa8e01e10d79d1784d549d70eb2cf53012`.
- JAR disassembly: `TileRequest.m_183515_(CompoundTag)` stores four UI fields, and omits `TileConnectable.m_183515_(CompoundTag)`; the latter stores `connectable` and calls `BlockEntity.m_183515_`.
- `ItemBuilder.getBlockState(Level,ItemStack)` calls `Level.m_246945_` with no null guard if `blockstate` exists.

## Alternatives
1. **Recommended: two narrow Mixin injections, separate files**, compiled against the official SSN 1.13.2 artifact but not bundled with it. Narrow behavior and clean removal.
2. Fork/repackage SSN. Rejected: duplicates upstream code and creates distribution/upgrade problems.
3. Reflection/event-based workarounds. Rejected where Mixin can invoke the native parent or return from a method with the exact bug.

## Behavior
### SSN-001
Inject at the end of `TileRequest.saveAdditional(CompoundTag)` and invoke the parent `TileConnectable.saveAdditional`. Leave existing SSN fields untouched. Resulting data contains `connectable` (including stored main position) plus original terminal settings. No unconditional network refresh, no mutation of item inventories. The network may recover links naturally today: this patch addresses the **persistence contract**, not an already reproduced catastrophic world bug.

### SSN-002
Inject at the beginning of `ItemBuilder.getBlockState(Level,ItemStack)`. For `level == null`, return `null` immediately. For non-null world, do not alter original behavior. This follows the original method's existing null sentinel for missing saved block state, so `appendHoverText` can use its ordinary fallback text rather than crash.

## Restrictions
- Forge 47.x, Minecraft 1.20.1, Java 17, mandatory SSN version range `[1.13.2,1.13.3)`.
- No bundling SSN or FLib binaries.
- Do not adjust other SSN behavior; no changes to Factotum.
- Do not declare an in-game fix until original failing condition and the patched condition have been tested in Minecraft.
- When using Mixins on upstream mod classes, map `saveAdditional` (inherited Minecraft method) to its runtime name; keep SSN's own `getBlockState` name unremapped.

## Pending verification
1. Inspect generated Mixin refmap and JAR manifest `MixinConfigs`.
2. Compile both class injections, run client and server startup.
3. For SSN-001: compare `TileRequest` saved NBT before/after; verify both UI fields and `connectable`, reload a world, test terminal function and older worlds.
4. For SSN-002: exercise null-world tooltip on builder remote with and without saved block state; check valid-world tooltip unchanged.
5. Confirm no crash when optional rendering details are requested; check no warning about unmatched injections.
