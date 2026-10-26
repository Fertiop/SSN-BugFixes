package io.github.fertiop.ssnbugfixes.mixin;

import java.util.Comparator;
import java.util.List;

import com.lothrazar.storagenetwork.api.DimPos;
import com.lothrazar.storagenetwork.api.IConnectableLink;
import com.lothrazar.storagenetwork.block.main.NetworkModule;
import com.lothrazar.storagenetwork.registry.StorageNetworkCapabilities;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SSN-003: keep the upstream same-target/double-chest deduplication, but
 * consider link priority before the first physical inventory is selected.
 * Equal priorities still use upstream's deterministic position ordering.
 */
@Mixin(value = NetworkModule.class, remap = false)
public abstract class NetworkPriorityDedupMixin {

    @Redirect(method = "getConnectableStorage()Ljava/util/Set;",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;sort(Ljava/util/Comparator;)V",
                    remap = false),
            remap = false)
    private void ssnbugfixes$sortByPriorityBeforeDedup(
            List<DimPos> positions, Comparator<? super DimPos> originalTieBreaker) {
        positions.sort(Comparator
                .comparingInt(NetworkPriorityDedupMixin::ssnbugfixes$priority)
                .thenComparing(originalTieBreaker));
    }

    private static int ssnbugfixes$priority(DimPos position) {
        if (position == null || !position.isLoaded()) {
            return Integer.MAX_VALUE;
        }
        try {
            BlockEntity block = position.getTileEntity(BlockEntity.class);
            if (block == null) {
                return Integer.MAX_VALUE;
            }
            IConnectableLink link = block.getCapability(
                    StorageNetworkCapabilities.CONNECTABLE_ITEM_STORAGE_CAPABILITY, null).orElse(null);
            return link != null ? link.getPriority() : Integer.MAX_VALUE;
        }
        catch (Exception exception) {
            return Integer.MAX_VALUE;
        }
    }
}
