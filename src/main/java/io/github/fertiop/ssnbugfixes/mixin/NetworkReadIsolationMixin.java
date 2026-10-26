package io.github.fertiop.ssnbugfixes.mixin;

import java.util.Collections;
import java.util.List;

import com.lothrazar.storagenetwork.StorageNetworkMod;
import com.lothrazar.storagenetwork.api.IConnectableLink;
import com.lothrazar.storagenetwork.block.main.NetworkModule;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SSN-006: one third-party item handler can throw from getStoredStacks.
 * Stock NetworkModule catches outside its for loop, which hides every
 * subsequent healthy inventory. Isolate failure to the offending link.
 *
 * Do not retry a provider after an exception; it may have side effects.
 */
@Mixin(value = NetworkModule.class, remap = false)
public abstract class NetworkReadIsolationMixin {

    @Redirect(
            method = {"getStacks(Z)Ljava/util/List;", "getSortedStacks()Ljava/util/List;"},
            at = @At(value = "INVOKE",
                    target = "Lcom/lothrazar/storagenetwork/api/IConnectableLink;getStoredStacks(Z)Ljava/util/List;",
                    remap = false),
            remap = false)
    private List<ItemStack> ssnbugfixes$readSafely(IConnectableLink storage, boolean filtered) {
        try {
            List<ItemStack> stacks = storage.getStoredStacks(filtered);
            return stacks != null ? stacks : Collections.emptyList();
        }
        catch (Exception exception) {
            StorageNetworkMod.LOGGER.warn(
                    "SSN-BugFixes: skipping one faulty storage provider; preserving other network inventories",
                    exception);
            return Collections.emptyList();
        }
    }
}
