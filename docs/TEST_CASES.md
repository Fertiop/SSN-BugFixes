# SSN 1.13.2 — regression acceptance cases

**Status: NOT RUN.** The project owner authorized development, not running builds or game tests. These cases must be executed before merge/release. Use a disposable Minecraft 1.20.1 Forge 47.3.0 test world and SSN 1.13.2 + FLib.

## SSN-001: Saved NBT of crafting/request terminal
- **Before (stock SSN):** place network master, connect crafting request terminal, configure sort/JEI/auto-focus, save terminal NBT. `TileRequest.saveAdditional` includes UI settings but omits inherited `connectable` tag. Inspect binary/world NBT to confirm.
- **After (with SSN-BugFixes):** saved NBT must contain original `autoFocus`, `storagenetworkdir`, `storagenetworksort`, `storagenetworkjei` plus parent `connectable` CompoundTag (with a connected `master`).
- **Reload:** save/close/reopen; terminal should still connect to correct network; check refresh with chunks unloaded, reconnect/unplug cases; no excess duplicated network entries.
- **Negative:** read a world made without the patch: absent inherited tag must not crash loading. Verify SSN's native rebuild when possible.

## SSN-002: Nullable level from item tooltip
- **Before (stock SSN):** `ItemBuilder.getBlockState(null, builderRemoteWithBlockstate)` reaches `level.holderLookup` and throws NPE.
- **After (with SSN-BugFixes):** return null with same remote and null Level; `appendHoverText` retains its fallback display.
- **Negative:** no `blockstate` tag and null Level must return null.
- **Non-null world:** set known block state (e.g. `minecraft:stone`) on remote; the decoded state must match expected; tooltip must display normal name.

## Diagnostics to preserve
- Gradle build output, Java version and exit code.
- Mixin refmap with mapping for inherited `saveAdditional` (production method `m_183515_`).
- Packaged JAR `MixinConfigs` manifest attribute.
- Client and dedicated server logs including Mixin injection count; test world backup; before/after NBT observations.
- Any error or failed case must be added to `docs/BUG_REGISTRY.md` with exact reproduction.
