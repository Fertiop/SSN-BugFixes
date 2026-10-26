package io.github.fertiop.ssnbugfixes;

import net.minecraftforge.fml.common.Mod;

/**
 * Bootstrap only. Version-gated patch classes will be added after
 * their SSN 1.13.2 root causes and regression cases are verified.
 */
@Mod(SsnBugFixes.MOD_ID)
public final class SsnBugFixes {

    public static final String MOD_ID = "ssnbugfixes";

    public SsnBugFixes() {
        // Deliberately empty: no bug fixes have been enabled yet.
    }
}
