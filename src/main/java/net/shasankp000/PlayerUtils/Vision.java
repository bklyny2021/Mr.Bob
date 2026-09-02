package net.shasankp000.PlayerUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Mr.Bob's sight. Builds a textual description of what the bot can "see"
 * around it: the blocks in each direction, nearby entities (mobs/players),
 * and hazards (lava, cliffs). This is fed to the LLM so the bot can
 * understand and describe its surroundings — real perception, not just a
 * single raycast.
 *
 * <p>This is additive: it does not touch the existing raycast or navigation
 * code, so all existing AI Player functions keep working.</p>
 */
public final class Vision {
    private static final Logger LOGGER = LoggerFactory.getLogger("ai-player-vision");

    private Vision() {}

    /** How far out (in blocks) Mr.Bob scans in each direction. */
    private static final int SCAN_RADIUS = 8;
    /** How far out he detects entities. */
    private static final int ENTITY_RADIUS = 16;

    /**
     * Builds a full textual description of what Mr.Bob sees around him.
     *
     * @param bot the bot (a ServerPlayer) whose surroundings to describe
     * @return a human-readable description of his view
     */
    public static String describeSurroundings(ServerPlayer bot) {
        if (bot == null) return "I can't see anything — I'm not in the world.";
        ServerLevel level = bot.level();
        BlockPos pos = bot.blockPosition();
        Direction facing = bot.getDirection();

        StringBuilder sb = new StringBuilder();
        sb.append("I am at x=").append(pos.getX())
          .append(", y=").append(pos.getY())
          .append(", z=").append(pos.getZ())
          .append(", facing ").append(facing.getName()).append(".\n");

        // 1. What's directly in front (raycast)
        sb.append("Directly in front of me: ").append(blockInFront(bot)).append(".\n");

        // 2. Blocks in each direction at eye level — 360° scan (8 compass points)
        sb.append("Around me I see (360° scan):\n");
        int[][] compass = {
                {0, -1},  // north
                {1, -1},  // north-east
                {1, 0},   // east
                {1, 1},   // south-east
                {0, 1},   // south
                {-1, 1},  // south-west
                {-1, 0},  // west
                {-1, -1}  // north-west
        };
        String[] compassNames = {"North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West"};
        for (int i = 0; i < compass.length; i++) {
            BlockPos probe = pos.offset(compass[i][0] * 3, 0, compass[i][1] * 3);
            Block block = level.getBlockState(probe).getBlock();
            String name = blockName(block);
            if (!name.equals("air")) {
                sb.append("  - ").append(compassNames[i]).append(": ").append(name).append("\n");
            }
        }

        // 3. Ground below and sky above
        Block below = level.getBlockState(pos.below()).getBlock();
        sb.append("  - Below me: ").append(blockName(below)).append("\n");
        Block above = level.getBlockState(pos.above(2)).getBlock();
        if (!blockName(above).equals("air")) {
            sb.append("  - Above me: ").append(blockName(above)).append("\n");
        }

        // 4. Nearby entities (mobs, animals, players)
        List<String> entities = nearbyEntities(bot, level);
        if (entities.isEmpty()) {
            sb.append("No mobs or players nearby.\n");
        } else {
            sb.append("Nearby entities:\n");
            for (String e : entities) sb.append("  - ").append(e).append("\n");
        }

        // 5. Hazards
        List<String> hazards = detectHazards(bot, level);
        if (!hazards.isEmpty()) {
            sb.append("DANGER: ").append(String.join(", ", hazards)).append("!\n");
        }

        return sb.toString();
    }

    /** Raycasts what block is directly in front of the bot. */
    private static String blockInFront(ServerPlayer bot) {
        Vec3 start = bot.getEyePosition();
        Vec3 dir = Vec3.atLowerCornerOf(bot.getDirection().getUnitVec3i());
        Vec3 end = start.add(dir.scale(6.0));
        var hit = bot.level().clip(new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.ANY,
                bot));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return blockName(bot.level().getBlockState(hit.getBlockPos()).getBlock());
        }
        return "open space";
    }

    /** Lists nearby entities with their type and distance. */
    private static List<String> nearbyEntities(ServerPlayer bot, ServerLevel level) {
        List<String> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e == bot) continue;
            if (!(e instanceof LivingEntity)) continue;
            double dist = bot.distanceTo(e);
            if (dist > ENTITY_RADIUS) continue;
            String type;
            if (e instanceof Monster) type = "hostile " + e.getName().getString();
            else if (e instanceof Animal) type = "animal " + e.getName().getString();
            else if (e instanceof ServerPlayer) type = "player " + e.getName().getString();
            else type = e.getName().getString();
            out.add(type + " at " + String.format("%.1f", dist) + " blocks");
        }
        return out;
    }

    /** Detects lava and cliff hazards around the bot. */
    private static List<String> detectHazards(ServerPlayer bot, ServerLevel level) {
        List<String> hazards = new ArrayList<>();
        BlockPos pos = bot.blockPosition();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                BlockPos probe = pos.offset(dx, 0, dz);
                Block b = level.getBlockState(probe).getBlock();
                if (b == Blocks.LAVA || b == Blocks.LAVA_CAULDRON) {
                    hazards.add("lava " + (int)Math.hypot(dx, dz) + " blocks away");
                    break;
                }
            }
        }
        // Cliff check: is there ground a few blocks ahead in the facing direction?
        Direction facing = bot.getDirection();
        BlockPos ahead = pos.offset(facing.getStepX() * 4, -1, facing.getStepZ() * 4);
        if (level.getBlockState(ahead).isAir()) {
            hazards.add("a cliff/drop ahead");
        }
        return hazards;
    }

    private static String blockName(Block block) {
        if (block == null) return "unknown";
        String id = block.getDescriptionId().replace("block.minecraft.", "");
        return id;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
