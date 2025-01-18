package com.leclowndu93150.spawnstone;

import net.blay09.mods.waystones.api.IMutableWaystone;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import java.util.Optional;

@Mod("spawnstone")
public class SpawnStone {

    private static ForgeConfigSpec.IntValue maxRange;
    private static ForgeConfigSpec.IntValue minRange;
    private static ForgeConfigSpec.IntValue maxAttempts;
    private static final String SPAWNSTONE_TAG = "spawnstone_placed";
    private static final String SPAWNSTONE_POS_TAG = "spawnstone_position";

    public SpawnStone() {
        setupConfig();
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setupConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        minRange = builder.comment("Minimum range from spawn to generate waystone")
                .defineInRange("minRange", 5, 1, Integer.MAX_VALUE);
        maxRange = builder.comment("Maximum range from spawn to generate waystone")
                .defineInRange("maxRange", 15, 1, Integer.MAX_VALUE);
        maxAttempts = builder.comment("Maximum attempts to place a waystone")
                .defineInRange("maxAttempts", 10, 1, Integer.MAX_VALUE);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, builder.build());
    }

    @SubscribeEvent
    public void onPlayerFirstJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag persistentData = player.getGameProfile().getProperties().containsKey(SPAWNSTONE_TAG)
                    ? new CompoundTag()
                    : player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

            if (!persistentData.contains(SPAWNSTONE_TAG)) {
                persistentData.putBoolean(SPAWNSTONE_TAG, true);
                for (int retry = 0; retry < maxAttempts.get(); retry++) {
                    BlockPos pos = generateWaystone(player);
                    if (pos != null) {
                        CompoundTag posTag = new CompoundTag();
                        posTag.putInt("x", pos.getX());
                        posTag.putInt("y", pos.getY());
                        posTag.putInt("z", pos.getZ());
                        persistentData.put(SPAWNSTONE_POS_TAG, posTag);
                        player.sendSystemMessage(Component.literal("A waystone has been generated for you at " + pos.toShortString()));
                        break;
                    }

                    if (retry == maxAttempts.get() - 1) {
                        player.sendSystemMessage(Component.literal("Failed to generate a waystone after " + maxAttempts.get() + " attempts."));
                    }
                }
                player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistentData);
            }
        }
    }

    private boolean canPlaceWaystone(BlockPos pos, ServerPlayer player) {
        BlockState blockState = player.level().getBlockState(pos);
        BlockState blockAbove = player.level().getBlockState(pos.above());
        BlockState blockBelow = player.level().getBlockState(pos.below());

        if (blockState.getFluidState().isSource() || blockAbove.getFluidState().isSource()) {
            return false;
        }

        return (blockState.canBeReplaced() || blockState.isAir()) &&
                (blockAbove.canBeReplaced() || blockAbove.isAir()) &&
                blockBelow.isSolid() &&
                !blockBelow.is(BlockTags.LEAVES);
    }

    private BlockPos generateWaystone(ServerPlayer player) {
        BlockPos spawnPos = player.serverLevel().getSharedSpawnPos();
        int minR = minRange.get();
        int maxR = maxRange.get();

        for (int attempts = 0; attempts < 50; attempts++) {
            int x = spawnPos.getX() + player.serverLevel().random.nextIntBetweenInclusive(minR, maxR) * (player.serverLevel().random.nextBoolean() ? 1 : -1);
            int z = spawnPos.getZ() + player.serverLevel().random.nextIntBetweenInclusive(minR, maxR) * (player.serverLevel().random.nextBoolean() ? 1 : -1);

            BlockPos testPos = player.serverLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));

            if (canPlaceWaystone(testPos, player)) {
                Optional<IWaystone> waystone = WaystonesAPI.placeWaystone(player.serverLevel(), testPos, WaystoneStyles.DEFAULT);
                if (waystone.isPresent()) {
                    ((IMutableWaystone)waystone.get()).setOwnerUid(player.getUUID());

                    BlockEntity blockEntity = player.serverLevel().getBlockEntity(testPos);
                    if (blockEntity instanceof WaystoneBlockEntityBase) {
                        ((WaystoneBlockEntityBase)blockEntity).initializeWaystone((ServerLevel)player.level(), player, WaystoneOrigin.WILDERNESS);

                        BlockEntity topEntity = player.serverLevel().getBlockEntity(testPos.above());
                        if (topEntity instanceof WaystoneBlockEntityBase) {
                            ((WaystoneBlockEntityBase)topEntity).initializeFromBase((WaystoneBlockEntityBase)blockEntity);
                        }
                    }

                    return testPos;
                }
            }
        }
        return null;
    }
}