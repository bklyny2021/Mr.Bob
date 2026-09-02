package net.shasankp000.PlayerUtils;

import carpet.fakes.ServerPlayerInterface;
import carpet.helpers.EntityPlayerActionPack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mr.Bob's cave-escape logic. When he falls into a hole/cave and can't get out,
 * he digs his way back up to the surface by pillar-jumping:
 *  1. Break any block directly above his head.
 *  2. JUMP.
 *  3. WHILE IN THE AIR, place a block under his feet.
 *  4. Land on it (now one block higher).
 *  5. Repeat until he reaches open sky (the surface).
 *  6. Once on the surface, he returns to whatever he was doing (follow/mine).
 *
 * This is the exact mechanic: jump, place a block under his feet mid-air,
 * land on it, then jump again.
 */
public final class CaveEscape {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-cave-escape");

    private CaveEscape() {}

    /**
     * Checks if Mr.Bob is stuck underground (no sky above him) and, if so,
     * digs him up to the surface by pillar-jumping.
     *
     * @param bot the bot to rescue
     * @return true if he was underground and is now escaping (or escaped)
     */
    public static boolean escapeIfStuck(ServerPlayer bot) {
        if (bot == null) return false;
        ServerLevel level = bot.level();

        // Is there sky above him? If yes, he's not stuck — nothing to do.
        if (hasSkyAbove(level, bot.blockPosition())) {
            return false;
        }

        LOGGER.info("🕳️ Mr.Bob is underground — starting pillar-jump escape");
        MrBobWiki.learn("I fell into a cave/hole and had to pillar-jump my way back to the surface");

        // Pillar-jump up until he reaches open sky.
        int maxSteps = 64; // safety cap
        int steps = 0;
        while (!hasSkyAbove(level, bot.blockPosition()) && steps < maxSteps) {
            steps++;
            BlockPos pos = bot.blockPosition();

            // 1. Break any block directly above his head (so he can rise).
            BlockPos above = pos.above(2);
            BlockState aboveState = level.getBlockState(above);
            if (!aboveState.isAir() && aboveState.getBlock() != Blocks.BEDROCK) {
                level.destroyBlock(above, true, bot);
                LOGGER.info("⛏️ Broke block above head at {}", above);
            }

            // 2. JUMP.
            jump(bot);

            // 3. WHILE IN THE AIR, place a block under his feet.
            //    The bot is now ~1 block up mid-jump, so place the block at the
            //    position he's rising from — that becomes the block he lands on.
            BlockPos feet = pos; // the spot he jumped from
            BlockState feetState = level.getBlockState(feet);
            if (feetState.isAir()) {
                level.setBlock(feet, Blocks.DIRT.defaultBlockState(), 3);
                LOGGER.info("🧱 Placed block under feet mid-air at {}", feet);
            }

            // 4. Land on it (snap up onto the block he just placed).
            bot.teleportTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        }

        if (hasSkyAbove(level, bot.blockPosition())) {
            LOGGER.info("☀️ Mr.Bob reached the surface!");
            MrBobWiki.learn("I escaped the cave and returned to the surface");
            return true;
        }
        return false;
    }

    /** Makes the bot jump (via the Carpet action pack). */
    private static void jump(ServerPlayer bot) {
        if (bot instanceof ServerPlayerInterface playerInterface) {
            EntityPlayerActionPack actions = playerInterface.getActionPack();
            actions.start(EntityPlayerActionPack.ActionType.JUMP, EntityPlayerActionPack.Action.once());
        }
        // Give the jump a moment to register so the block placement happens mid-air.
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {}
    }

    /** True if there's open sky (air) above the given position. */
    private static boolean hasSkyAbove(ServerLevel level, BlockPos pos) {
        // Check a column of air above — if we hit sky within a few blocks, he's not stuck.
        for (int y = pos.getY() + 1; y <= pos.getY() + 6; y++) {
            BlockState state = level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
            if (!state.isAir()) {
                return false; // a block above — he's underground
            }
        }
        return true; // open sky above
    }
}
