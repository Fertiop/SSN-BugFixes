# SSN-BugFixes — agent instructions

## Mission
Independent bug-fix companion mod for **Simple Storage Network 1.13.2 on Minecraft 1.20.1 / Forge 47.x / Java 17**. Fix *SSN's own defects*, not Factotum integration bugs.

## Scope and invariants
- Do not edit, redistribute or repack SSN's original JAR. Do not modify Factotum here.
- Preserve item count and NBT across insert, request, crafting, world save/load and exceptions. Never silently drop, duplicate, or synthesize resources.
- Patches must be target-version guarded and narrowly scoped; prefer reversible Mixins to copying whole upstream classes.
- No unsafe retry of a storage operation after an uncertain or partial commit.
- Do not add a fix because a GitHub issue exists: verify against SSN 1.13.2 source/binary, trace the end-to-end execution path and try to disprove the hypothesis.
- Target single-player and trusted small multiplayer servers; avoid speculative public-server threat-model features.

## Before changing code
1. Inspect `docs/BUG_REGISTRY.md` to prevent duplicates and update existing findings in place.
2. Read the upstream `Lothrazar/Storage-Network` `trunk/1.20.1` source **and check the actual 1.13.2 JAR** before asserting runtime identity.
3. Produce minimal reproduction, root cause, counterarguments, failure cases, and a regression test plan.
4. Keep fixes isolated and configurable where possible.
5. Do not run builds or game tests or claim success without a verified run and the project owner's authorization.

## Status language
- `hypothesis` — plausible; not demonstrated
- `static-confirmed` — code path independently checked; no in-game reproduction
- `reproduced` — failed in controlled in-game test
- `fixed-unverified` — code patch present but not proven
- `verified-fixed` — repeatable regression test and survival checks pass

Never conflate an untested starter project with a working fix.
