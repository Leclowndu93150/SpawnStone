package com.leclowndu93150.spawnstone;

import net.blay09.mods.waystones.block.ModBlocks;
import net.blay09.mods.waystones.block.WaystoneBlock;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod("spawnstone")
public class SpawnStone {

    private static ForgeConfigSpec.IntValue maxRange;
    private static ForgeConfigSpec.IntValue minRange;
    private static ForgeConfigSpec.BooleanValue forceUnbreakable;
    private static final String SPAWNSTONE_TAG = "spawnstone_placed";
    private static final String SPAWNSTONE_POS_TAG = "spawnstone_position";

    public SpawnStone() {
        setupConfig();
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setupConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("SpawnStone Configuration");

        minRange = builder.comment("Minimum range from spawn to generate waystone")
                .defineInRange("minRange", 5, 1, 50);

        maxRange = builder.comment("Maximum range from spawn to generate waystone")
                .defineInRange("maxRange", 15, 1, 100);

        forceUnbreakable = builder.comment("Force waystone to be unbreakable regardless of Waystones mod config")
                .define("forceUnbreakable", true);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, builder.build());
    }

    @SubscribeEvent
    public void onPlayerFirstJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();

            if (!data.contains(SPAWNSTONE_TAG)) {
                data.putBoolean(SPAWNSTONE_TAG, true);
                BlockPos pos = generateWaystone(player);
                if (pos != null) {
                    CompoundTag posTag = new CompoundTag();
                    posTag.putInt("x", pos.getX());
                    posTag.putInt("y", pos.getY());
                    posTag.putInt("z", pos.getZ());
                    data.put(SPAWNSTONE_POS_TAG, posTag);
                }
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!forceUnbreakable.get()) return;

        BlockPos pos = event.getPos();
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        CompoundTag data = player.getPersistentData();

        if (data.contains(SPAWNSTONE_POS_TAG)) {
            CompoundTag posTag = data.getCompound(SPAWNSTONE_POS_TAG);
            BlockPos spawnstonePos = new BlockPos(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")
            );

            if (pos.equals(spawnstonePos) || pos.equals(spawnstonePos.above())) {
                event.setCanceled(true);
            }
        }
    }

    private BlockPos generateWaystone(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos spawnPos = level.getSharedSpawnPos();

        int minR = minRange.get();
        int maxR = maxRange.get();

        for(int attempts = 0; attempts < 50; attempts++) {
            int x = spawnPos.getX() + level.random.nextIntBetweenInclusive(minR, maxR) * (level.random.nextBoolean() ? 1 : -1);
            int z = spawnPos.getZ() + level.random.nextIntBetweenInclusive(minR, maxR) * (level.random.nextBoolean() ? 1 : -1);

            BlockPos pos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));

            if (canPlaceWaystone(level, pos)) {
                placeWaystone(level, pos);
                return pos;
            }
        }
        return null;
    }

    private boolean canPlaceWaystone(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() &&
                level.getBlockState(pos.above()).isAir() &&
                !level.getBlockState(pos.below()).isAir();
    }

    private void placeWaystone(ServerLevel level, BlockPos pos) {
        Direction facing = Direction.from2DDataValue(level.random.nextInt(4));

        BlockState lowerState = ModBlocks.waystone.defaultBlockState()
                .setValue(WaystoneBlock.FACING, facing)
                .setValue(WaystoneBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(WaystoneBlockBase.ORIGIN, WaystoneOrigin.WILDERNESS);

        BlockState upperState = lowerState.setValue(WaystoneBlock.HALF, DoubleBlockHalf.UPPER);

        level.setBlock(pos, lowerState, 3);
        level.setBlock(pos.above(), upperState, 3);

        WaystoneBlockEntity blockEntity = (WaystoneBlockEntity) level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.initializeWaystone(level, null, WaystoneOrigin.WILDERNESS);

            WaystoneBlockEntity upperEntity = (WaystoneBlockEntity) level.getBlockEntity(pos.above());
            if (upperEntity != null) {
                upperEntity.initializeFromBase(blockEntity);
            }
        }
    }
}