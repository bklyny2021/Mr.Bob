package net.shasankp000.PlayerUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Mr.Bob's "dig down for ore" behavior.
 *
 * <p>When asked to mine, he digs straight down through the ground, checking
 * each block for ore. When he finds ore (or reaches a depth limit), he stops
 * and uses {@link CaveEscape} to dig his way back up to the surface, then
 * returns to FOLLOW mode.</p>
 *
 * <p>This is the opposite of the cave-escape: instead of digging up, he digs
 * down to find resources, then escapes back up.</p>
 */
public final class MineDown {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-mine-down");

    private MineDown() {}

    /** Blocks that count as "ore" worth digging for. */
    private static final Set<Block> ORES = Set.of(
            Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
            Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.EMERALD_ORE, Blocks.COPPER_ORE,
            Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.ANCIENT_DEBRIS, Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE
    );

    /** Max blocks to dig down before giving up. */
    private static final int MAX_DIG_DEPTH = 40;

    /**
     * Digs straight down until ore is found (or depth limit), then escapes
     * back up to the surface.
     *
     * @param bot the bot to mine with
     * @return a short description of what happened
     */
    public static String digForOre(ServerPlayer bot) {
        if (bot == null) return "I'm not in the world.";
        ServerLevel level = bot.level();
        BlockPos start = bot.blockPosition();
        int dug = 0;
        Block foundOre = null;

        LOGGER.info("⛏️ Mr.Bob digging down for ore from {}", start);

        // Dig straight down, checking each block for ore.
        for (int depth = 1; depth <= MAX_DIG_DEPTH; depth++) {
            BlockPos below = start.below(depth);
            if (!level.isLoaded(below)) break;
            BlockState state = level.getBlockState(below);
            Block block = state.getBlock();

            // Stop if we hit bedrock or lava (can't dig further safely).
            if (block == Blocks.BEDROCK || block == Blocks.LAVA) {
                LOGGER.info("Stopped digging at {} (bedrock/lava)", below);
                break;
            }

            // Break the block below and move down onto it.
            if (!state.isAir()) {
                level.destroyBlock(below, true, bot);
                dug++;
            }
            bot.teleportTo(below.getX() + 0.5, below.getY() + 1, below.getZ() + 0.5);

            // Check if we found ore.
            if (ORES.contains(block)) {
                foundOre = block;
                LOGGER.info("💎 Found ore: {} at {}", block, below);
                MrBobWiki.learn("I mined " + block.getDescriptionId().replace("block.minecraft.", "")
                        + " at x=" + below.getX() + " y=" + below.getY() + " z=" + below.getZ());
                break;
            }
        }

        // Now escape back up to the surface.
        LOGGER.info("🕳️ Digging back up to the surface");
        CaveEscape.escapeIfStuck(bot);

        if (foundOre != null) {
            return "I dug down and found " + foundOre.getDescriptionId().replace("block.minecraft.", "")
                    + " (dug " + dug + " blocks), then climbed back up.";
        }
        return "I dug down " + dug + " blocks but didn't find ore, then climbed back up.";
    }
}
