package io.github.fertiop.ssnbugfixes.mixin;

import com.lothrazar.storagenetwork.block.TileConnectable;
import com.lothrazar.storagenetwork.block.request.TileRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SSN-001: TileRequest overrides saveAdditional without calling the
 * TileConnectable implementation, which persists the "connectable" tag.
 *
 * Using the real parent method also preserves any vanilla/Forge block entity
 * state the parent might serialize. The injection leaves SSN's own fields
 * and their exact layout unchanged.
 */
@Mixin(TileRequest.class)
public abstract class TileRequestSaveMixin extends TileConnectable {

    protected TileRequestSaveMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void ssnbugfixes$includeConnectable(CompoundTag tag, CallbackInfo ci) {
        super.saveAdditional(tag);
    }
}
