package com.leclowndu93150.spawnstone.mixin;

import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerWaystoneManager.class)
public class PlayerWaystoneManagerMixin {
    private static final String SPAWNSTONE_POS_TAG = "spawnstone_position";

    @Inject(method = "mayBreakWaystone", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onMayBreakWaystone(Player player, BlockGetter world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        CompoundTag data = player.getPersistentData();
        if (data.contains(SPAWNSTONE_POS_TAG)) {
            CompoundTag posTag = data.getCompound(SPAWNSTONE_POS_TAG);
            BlockPos spawnstonePos = new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")
            );
            if (pos.equals(spawnstonePos) || pos.equals(spawnstonePos.above())) {
                System.out.println("Spawnstone cannot be broken");
                cir.setReturnValue(false);
            }
        }
    }
}