package io.github.fertiop.ssnbugfixes.mixin;

import com.lothrazar.storagenetwork.item.ItemBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SSN-002: vanilla tooltips may call appendHoverText with a null Level.
 * ItemBuilder.getBlockState dereferences that Level when an ItemStack carries
 * "blockstate" NBT. Keep all non-null lookups entirely in upstream SSN.
 */
@Mixin(value = ItemBuilder.class, remap = false)
public abstract class ItemBuilderNullLevelMixin {

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ssnbugfixes$nullWorld(
            Level level, ItemStack stack, CallbackInfoReturnable<BlockState> cir) {
        if (level == null) {
            cir.setReturnValue(null);
        }
    }
}
