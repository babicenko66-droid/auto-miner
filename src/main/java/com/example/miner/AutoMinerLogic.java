package com.example.miner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * \u0410\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440: \u043a\u043e\u043f\u0430\u0435\u0442 \u0442\u0443\u043d\u043d\u0435\u043b\u0438 \u042d\u0444\u0444\u0435\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c\u044e, \u0430\u043b\u043c\u0430\u0437\u043d\u0443\u044e \u0440\u0443\u0434\u0443 \u0431\u0435\u0440\u0451\u0442 \u0428\u0451\u043b\u043a\u043e\u043c,
 * \u0441\u043b\u0435\u0434\u0438\u0442 \u0437\u0430 \u043f\u0440\u043e\u0447\u043d\u043e\u0441\u0442\u044c\u044e, \u0435\u0434\u043e\u0439, \u043b\u0430\u0432\u043e\u0439, \u043c\u043e\u0431\u0430\u043c\u0438, \u043f\u043e\u0434\u0431\u0438\u0440\u0430\u0435\u0442 \u0434\u0440\u043e\u043f, \u0438\u043d\u043e\u0433\u0434\u0430 \u043c\u0435\u043d\u044f\u0435\u0442 \u0432\u044b\u0441\u043e\u0442\u0443.
 *
 * \u041a\u043e\u043c\u0430\u043d\u0434\u044b \u0432 \u0447\u0430\u0442\u0435: .miner | .miner hud | .miner stats | .miner reset | .miner safe | .miner help
 */
public class AutoMinerLogic {

    // ================================================================== \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438

    /** \u041d\u0438\u0436\u0435 \u044d\u0442\u043e\u0439 \u0432\u044b\u0441\u043e\u0442\u044b \u0431\u043e\u0442 \u043d\u0438\u043a\u043e\u0433\u0434\u0430 \u043d\u0435 \u043e\u043f\u0443\u0441\u043a\u0430\u0435\u0442\u0441\u044f (\u0442\u0430\u043c \u0431\u0435\u0434\u0440\u043e\u043a). */
    private static final int MIN_Y = -59;
    /** \u0414\u0438\u0430\u043f\u0430\u0437\u043e\u043d \u0432\u044b\u0441\u043e\u0442, \u043c\u0435\u0436\u0434\u0443 \u043a\u043e\u0442\u043e\u0440\u044b\u043c\u0438 \u0431\u043e\u0442 \u0438\u043d\u043e\u0433\u0434\u0430 \u043f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0430\u0435\u0442\u0441\u044f. */
    private static final int HEIGHT_MIN = -59;
    private static final int HEIGHT_MAX = -54;
    /** \u0428\u0430\u043d\u0441 (\u0432 %) \u0441\u043c\u0435\u043d\u0438\u0442\u044c \u0432\u044b\u0441\u043e\u0442\u0443 \u043a\u0430\u0436\u0434\u044b\u0439 \u0440\u0430\u0437, \u043a\u043e\u0433\u0434\u0430 \u0431\u043e\u0442 \u0437\u0430\u043a\u0430\u043d\u0447\u0438\u0432\u0430\u0435\u0442 \u043f\u0440\u044f\u043c\u043e\u0439 \u0443\u0447\u0430\u0441\u0442\u043e\u043a \u0438 \u043f\u043e\u0432\u043e\u0440\u0430\u0447\u0438\u0432\u0430\u0435\u0442. */
    private static final int HEIGHT_CHANGE_PERCENT = 0;

    private static final int SAFE_DURABILITY = 10;
    private static final int LAVA_LOOKAHEAD = 6;
    private static final int LAVA_SIDE = 2;
    private static final int FALLING_LOOKAHEAD = 3;
    private static final int STRAIGHT_MIN = 30;
    private static final int STRAIGHT_RANDOM = 20;
    private static final float MIN_HEALTH = 8.0f;
    private static final int EAT_BELOW = 14;
    private static final double ENGAGE_RANGE = 3.5d;
    private static final int JUNK_STACKS_TRIGGER = 3;
    private static final int MAX_DROPS_PER_RUN = 4;
    private static final int STUCK_TICKS = 400;

    /** \u0421\u043a\u043e\u043b\u044c\u043a\u043e \u0442\u0438\u043a\u043e\u0432 \u0436\u0434\u0430\u0442\u044c, \u043f\u043e\u043a\u0430 \u043f\u0440\u0438\u0446\u0435\u043b \u0432\u0441\u0442\u0430\u043d\u0435\u0442 \u043d\u0430 \u0431\u043b\u043e\u043a, \u043f\u0440\u0435\u0436\u0434\u0435 \u0447\u0435\u043c \u0441\u0434\u0430\u0442\u044c\u0441\u044f. */
    private static final int AIM_TIMEOUT = 30;
    /** \u041e\u043a\u043d\u043e (\u0432 \u0442\u0438\u043a\u0430\u0445) \u043d\u0430 \u0441\u0431\u043e\u0440 \u0432\u044b\u043f\u0430\u0432\u0448\u0438\u0445 \u043f\u0440\u0435\u0434\u043c\u0435\u0442\u043e\u0432. */
    private static final int PICKUP_WINDOW = 60;
    private static final double PICKUP_RADIUS = 6.0d;
    /** \u0420\u0430\u0434\u0438\u0443\u0441 \u043f\u043e\u0438\u0441\u043a\u0430 \u0432\u0438\u0434\u0438\u043c\u043e\u0439 \u0440\u0443\u0434\u044b \u0438 \u043c\u0430\u043a\u0441\u0438\u043c\u0430\u043b\u044c\u043d\u0430\u044f \u0434\u0438\u0441\u0442\u0430\u043d\u0446\u0438\u044f \u0434\u043e \u043d\u0435\u0451 (\u0432 \u0431\u043b\u043e\u043a\u0430\u0445). */
    private static final int ORE_SCAN = 5;
    private static final double ORE_REACH = 5.0d;
    /** \u0415\u0441\u043b\u0438 \u0440\u044f\u0434\u043e\u043c \u043f\u043e\u044f\u0432\u0438\u043b\u0441\u044f \u0434\u0440\u0443\u0433\u043e\u0439 \u0438\u0433\u0440\u043e\u043a \u043d\u0430 \u0442\u0430\u043a\u043e\u043c \u0440\u0430\u0441\u0441\u0442\u043e\u044f\u043d\u0438\u0438 \u2014 \u0432\u044b\u043a\u043b\u044e\u0447\u0430\u0435\u043c\u0441\u044f (.miner safe). */
    private static final double PLAYER_RADIUS = 24.0d;
    /** \u0421\u043a\u043e\u043b\u044c\u043a\u043e \u0442\u0438\u043a\u043e\u0432 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c \u043c\u043e\u0436\u0435\u0442 \u0431\u044b\u0442\u044c \u0437\u0430\u0431\u0438\u0442 \u0431\u0435\u0437 \u043c\u0443\u0441\u043e\u0440\u0430, \u043f\u0440\u0435\u0436\u0434\u0435 \u0447\u0435\u043c \u0431\u043e\u0442 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0441\u044f. */
    private static final int FULL_INVENTORY_TICKS = 100;
    /** \u0421\u043a\u043e\u043b\u044c\u043a\u043e \u0442\u0438\u043a\u043e\u0432 \u0431\u0435\u0437 \u043d\u0443\u0436\u043d\u043e\u0439 \u043a\u0438\u0440\u043a\u0438, \u043f\u0440\u0435\u0436\u0434\u0435 \u0447\u0435\u043c \u0431\u043e\u0442 \u043e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u0441\u044f. */
    private static final int NO_TOOL_TICKS = 100;

    private static final Set<Block> TARGET_ORES = Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);

    private static final Set<Item> FOOD = Set.of(
            Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON,
            Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BREAD,
            Items.BAKED_POTATO, Items.GOLDEN_CARROT, Items.CARROT, Items.APPLE, Items.PUMPKIN_PIE);

    private static final Set<Item> JUNK = Set.of(
            Blocks.COBBLESTONE.asItem(), Blocks.COBBLED_DEEPSLATE.asItem(), Blocks.STONE.asItem(),
            Blocks.DEEPSLATE.asItem(), Blocks.TUFF.asItem(), Blocks.DIRT.asItem(), Blocks.GRAVEL.asItem(),
            Blocks.ANDESITE.asItem(), Blocks.DIORITE.asItem(), Blocks.GRANITE.asItem(), Blocks.CALCITE.asItem(),
            Items.FLINT, Items.ROTTEN_FLESH, Items.BONE, Items.SPIDER_EYE, Items.ARROW);

    private static final RandomSource RANDOM = RandomSource.create();

    // ================================================================== \u0441\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435

    private static boolean enabled = false;
    private static boolean hudVisible = true;
    private static boolean safeMode = true;
    private static LocalPlayer owner = null;

    private static Direction currentDirection = null;
    private static BlockPos lastPos = null;
    private static BlockPos stuckPos = null;
        private static boolean isReturningToSafeY = false;
    private static final java.util.Set<BlockPos> IGNORED_LOW_ORES = new java.util.HashSet<>();

    private static int stuckTicks = 0;
    private static int blocksDugInStraightLine = 0;
    private static int messageCooldown = 0;
    private static int junkCooldown = 0;
    private static int dropPhase = 0;

    private static BlockPos mineTarget = null;
    private static int aimMiss = 0;
    private static BlockPos oreTarget = null;
    private static int pickupTicks = 0;

    private static int targetY = HEIGHT_MIN;
    private static BlockPos walkPos = null;
    private static int walkStall = 0;
    private static int centerTicks = 0;

    private static int fullTicks = 0;
    private static int noToolTicks = 0;

    /** \u0420\u0443\u0434\u044b, \u0434\u043e \u043a\u043e\u0442\u043e\u0440\u044b\u0445 \u043f\u0440\u0438\u0446\u0435\u043b \u043d\u0435 \u0434\u043e\u0442\u044f\u043d\u0443\u043b\u0441\u044f (\u0437\u0430\u043a\u0440\u044b\u0442\u044b \u0434\u0440\u0443\u0433\u0438\u043c \u0431\u043b\u043e\u043a\u043e\u043c). */
    private static final Set<BlockPos> IGNORED = new HashSet<>();
    /** \u041f\u0440\u0435\u0434\u043c\u0435\u0442\u044b, \u0434\u043e \u043a\u043e\u0442\u043e\u0440\u044b\u0445 \u043d\u0435 \u043f\u043e\u043b\u0443\u0447\u0438\u043b\u043e\u0441\u044c \u0434\u043e\u0439\u0442\u0438. */
    private static final Set<Integer> GIVE_UP = new HashSet<>();

    // ---- \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430 (\u043f\u043e\u043a\u0430\u0437\u044b\u0432\u0430\u0435\u0442\u0441\u044f \u0432 \u043f\u0430\u043d\u0435\u043b\u0438)
    private static int oresMined = 0;
    private static int blocksDug = 0;
    private static long runTicks = 0;
    private static String status = "\u043e\u0436\u0438\u0434\u0430\u043d\u0438\u0435";

    // ================================================================== \u043a\u043e\u043c\u0430\u043d\u0434\u044b / \u0443\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435

    public static void command(Minecraft mc, String arg) {
        switch (arg.toLowerCase()) {
            case "" -> toggle(mc);
            case "hud" -> {
                hudVisible = !hudVisible;
                chat(mc, hudVisible ? "\u00a7a\u041f\u0430\u043d\u0435\u043b\u044c \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0438 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u0430" : "\u00a7e\u041f\u0430\u043d\u0435\u043b\u044c \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0438 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u0430");
            }
            case "stats" -> {
                for (String line : hudLines(mc)) chat(mc, line);
            }
            case "reset" -> {
                oresMined = 0;
                blocksDug = 0;
                runTicks = 0;
                chat(mc, "\u00a7e\u0421\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430 \u0441\u0431\u0440\u043e\u0448\u0435\u043d\u0430");
            }
            case "safe" -> {
                safeMode = !safeMode;
                chat(mc, safeMode
                        ? "\u00a7a\u0417\u0430\u0449\u0438\u0442\u0430 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u0430: \u0431\u043e\u0442 \u0432\u044b\u043a\u043b\u044e\u0447\u0438\u0442\u0441\u044f, \u0435\u0441\u043b\u0438 \u0440\u044f\u0434\u043e\u043c \u043f\u043e\u044f\u0432\u0438\u0442\u0441\u044f \u0434\u0440\u0443\u0433\u043e\u0439 \u0438\u0433\u0440\u043e\u043a"
                        : "\u00a7e\u0417\u0430\u0449\u0438\u0442\u0430 \u043e\u0442 \u0438\u0433\u0440\u043e\u043a\u043e\u0432 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d\u0430");
            }
            default -> {
                chat(mc, "\u00a7b.miner \u00a77\u2014 \u0432\u043a\u043b/\u0432\u044b\u043a\u043b");
                chat(mc, "\u00a7b.miner hud \u00a77\u2014 \u043f\u0430\u043d\u0435\u043b\u044c \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0438");
                chat(mc, "\u00a7b.miner stats \u00a77\u2014 \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430 \u0432 \u0447\u0430\u0442");
                chat(mc, "\u00a7b.miner reset \u00a77\u2014 \u0441\u0431\u0440\u043e\u0441\u0438\u0442\u044c \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0443");
                chat(mc, "\u00a7b.miner safe \u00a77\u2014 \u0432\u044b\u043a\u043b\u044e\u0447\u0430\u0442\u044c\u0441\u044f, \u0435\u0441\u043b\u0438 \u0440\u044f\u0434\u043e\u043c \u0438\u0433\u0440\u043e\u043a");
            }
        }
    }

    public static void toggle(Minecraft mc) {
        enabled = !enabled;
        resetRun();
        releaseKeys(mc);
        status = enabled ? "\u0441\u0442\u0430\u0440\u0442" : "\u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d";
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal(enabled
                    ? "\u00a7a\u0410\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u043a\u043b\u044e\u0447\u0451\u043d"
                    : "\u00a7e\u0410\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d"));
        }
    }

    private static void resetRun() {
        currentDirection = null;
        lastPos = null;
        stuckPos = null;
        stuckTicks = 0;
        blocksDugInStraightLine = 0;
        dropPhase = 0;
        mineTarget = null;
        aimMiss = 0;
        oreTarget = null;
        pickupTicks = 0;
        targetY = HEIGHT_MIN;
        walkPos = null;
        walkStall = 0;
        centerTicks = 0;
        fullTicks = 0;
        noToolTicks = 0;
        IGNORED.clear();
        GIVE_UP.clear();
    }

    private static void disable(Minecraft mc, String reason) {
        enabled = false;
        dropPhase = 0;
        pickupTicks = 0;
        mineTarget = null;
        status = "\u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d";
        releaseKeys(mc);
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(reason));
        }
    }

    private static void releaseKeys(Minecraft mc) {
        if (mc.options != null) {
            mc.options.keyUp.setDown(false);
            mc.options.keyUse.setDown(false);
            mc.options.keyAttack.setDown(false);
            mc.options.keyJump.setDown(false);
        }
    }

    private static void warn(Minecraft mc, String text) {
        if (messageCooldown <= 0 && mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal(text));
            messageCooldown = 40;
        }
    }

    private static void chat(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }

    // ================================================================== \u043f\u0430\u043d\u0435\u043b\u044c \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0438

    public static boolean isHudVisible() {
        return hudVisible;
    }

    public static List<String> hudLines(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        lines.add("\u00a7b\u00a7lAuto-Miner " + (enabled ? "\u00a7a[\u0412\u041a\u041b]" : "\u00a7c[\u0412\u042b\u041a\u041b]"));
        lines.add("\u00a77\u0410\u043b\u043c\u0430\u0437\u043d\u0430\u044f \u0440\u0443\u0434\u0430: \u00a7b\u00a7l" + oresMined + "\u00a7r \u00a77(\u0432 \u0438\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u0435: \u00a7f" + countOreInInventory(mc) + "\u00a77)");
        lines.add("\u00a77\u0411\u043b\u043e\u043a\u043e\u0432 \u0432\u044b\u043a\u043e\u043f\u0430\u043d\u043e: \u00a7f" + blocksDug);

        long sec = runTicks / 20;
        String time = sec >= 3600
                ? String.format("%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)
                : String.format("%d:%02d", sec / 60, sec % 60);
        if (sec >= 60 && oresMined > 0) {
            time += " \u00a77(\u2248" + (oresMined * 3600L / sec) + " \u0440\u0443\u0434\u044b/\u0447\u0430\u0441)";
        }
        lines.add("\u00a77\u0412\u0440\u0435\u043c\u044f \u0440\u0430\u0431\u043e\u0442\u044b: \u00a7f" + time);

        int y = mc.player != null ? mc.player.blockPosition().getY() : 0;
        lines.add("\u00a77\u0412\u044b\u0441\u043e\u0442\u0430: \u00a7f" + y + " \u00a77(\u0446\u0435\u043b\u044c \u00a7f" + targetY + "\u00a77) | \u00a7f" + directionName());
        lines.add("\u00a77\u0421\u0442\u0430\u0442\u0443\u0441: \u00a7f" + status);
        return lines;
    }

    private static String directionName() {
        if (currentDirection == Direction.NORTH) return "\u0441\u0435\u0432\u0435\u0440";
        if (currentDirection == Direction.SOUTH) return "\u044e\u0433";
        if (currentDirection == Direction.EAST) return "\u0432\u043e\u0441\u0442\u043e\u043a";
        if (currentDirection == Direction.WEST) return "\u0437\u0430\u043f\u0430\u0434";
        return "-";
    }

    private static int countOreInInventory(Minecraft mc) {
        if (mc.player == null) return 0;
        Inventory inv = mc.player.getInventory();
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            Item it = s.getItem();
            if (it == Blocks.DIAMOND_ORE.asItem() || it == Blocks.DEEPSLATE_DIAMOND_ORE.asItem()) {
                total += s.getCount();
            }
        }
        return total;
    }

    // ================================================================== \u0433\u043b\u0430\u0432\u043d\u044b\u0439 \u0442\u0438\u043a

    public static void tick(Minecraft mc) {
        // \u0441\u043c\u0435\u043d\u0430 \u043c\u0438\u0440\u0430 / \u0440\u0435\u0441\u043f\u0430\u0443\u043d: \u043d\u043e\u0432\u044b\u0439 \u043e\u0431\u044a\u0435\u043a\u0442 \u0438\u0433\u0440\u043e\u043a\u0430 \u2014 \u0431\u043e\u0442 \u043d\u0435 \u0434\u043e\u043b\u0436\u0435\u043d \u0432\u043a\u043b\u044e\u0447\u0438\u0442\u044c\u0441\u044f \u0441\u0430\u043c
        if (mc.player != owner) {
            owner = mc.player;
            if (enabled) {
                enabled = false;
                resetRun();
                status = "\u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d";
            }
        }
        if (!enabled) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        try {
          if (handleLowHeightLogic(mc, mc.level, mc.player)) return;
  run(mc);
        } catch (RuntimeException e) {
            // \u043e\u0448\u0438\u0431\u043a\u0430 \u0432 \u043b\u043e\u0433\u0438\u043a\u0435 \u043d\u0435 \u0434\u043e\u043b\u0436\u043d\u0430 \u0440\u043e\u043d\u044f\u0442\u044c \u0438\u0433\u0440\u0443
            e.printStackTrace();
            disable(mc, "\u00a7c\u041e\u0448\u0438\u0431\u043a\u0430 \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440\u0430: " + e);
        } finally {
            if (!enabled) releaseKeys(mc);
        }
    }
    private static boolean handleLowHeightLogic(Minecraft mc, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.client.player.LocalPlayer player) {
              if (pickupTicks > 0) {
            return false;
        }

        BlockPos playerPos = player.blockPosition();
        if (playerPos.getY() < MIN_Y) {
            BlockPos diamondPos = findNearestOre(level, playerPos, ORE_SCAN);
            if (diamondPos != null && !IGNORED_LOW_ORES.contains(diamondPos)) {
                isReturningToSafeY = false;
                mineBlockPos(mc, level, player, diamondPos);
                return true; 
            } else {
                isReturningToSafeY = true;
            }
        }
        if (isReturningToSafeY) {
            if (playerPos.getY() >= MIN_Y) {
                isReturningToSafeY = false;
                if (mc.options.keyJump.isDown()) {
                    mc.options.keyJump.setDown(false);
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aВернулся на безопасную высоту. Продолжаю майнинг."));

                return false; 
            } else {
                BlockPos upPos = playerPos.above(2);
                net.minecraft.world.level.block.state.BlockState upState = level.getBlockState(upPos);
                if (!upState.isAir() && upState.getBlock() != net.minecraft.world.level.block.Blocks.BEDROCK) {
                    mineBlockPos(mc, level, player, upPos);
                } else {
                    mc.options.keyJump.setDown(true);
                }
                return true; 
            }
        }
        return false;
    }

    private static BlockPos findNearestOre(net.minecraft.client.multiplayer.ClientLevel level, BlockPos startPos, int radius) {
        BlockPos nearest = null;
        double closestDist = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos target = startPos.offset(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(target);
                    if (TARGET_ORES.contains(state.getBlock())) {
                        double dist = startPos.distSqr(target);
                        if (dist < closestDist) {
                            closestDist = dist;
                            nearest = target;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static void mineBlockPos(Minecraft mc, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.client.player.LocalPlayer player, BlockPos pos) {
        net.minecraft.world.phys.Vec3 targetVec = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        net.minecraft.world.phys.Vec3 playerEye = player.getEyePosition(1.0F);
        double dX = targetVec.x - playerEye.x;
        double dY = targetVec.y - playerEye.y;
        double dZ = targetVec.z - playerEye.z;
        double dXZ = Math.sqrt(dX * dX + dZ * dZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(dZ, dX)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(dY, dXZ));
        player.setYRot(yaw);
        player.setXRot(pitch);
        mc.gameMode.startDestroyBlock(pos, net.minecraft.core.Direction.UP);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (pos.getY() < MIN_Y && level.getBlockState(pos).getBlock() == net.minecraft.world.level.block.Blocks.BEDROCK) {
            IGNORED_LOW_ORES.add(pos);
        }
    }

    private static void run(Minecraft mc) {
        releaseKeys(mc);
        if (messageCooldown > 0) messageCooldown--;
        if (junkCooldown > 0) junkCooldown--;
        if (mc.screen != null) return;
        runTicks++;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        BlockPos pos = player.blockPosition();

        if (currentDirection == null) currentDirection = player.getDirection();

        if (player.getHealth() < MIN_HEALTH) {
            disable(mc, "\u00a7c\u041c\u0430\u043b\u043e \u0437\u0434\u043e\u0440\u043e\u0432\u044c\u044f, \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
            return;
        }
        if (player.isOnFire()) {
            disable(mc, "\u00a7c\u0412\u044b \u0433\u043e\u0440\u0438\u0442\u0435 (\u043b\u0430\u0432\u0430/\u043e\u0433\u043e\u043d\u044c), \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
            return;
        }

        // --- \u0434\u0440\u0443\u0433\u0438\u0435 \u0438\u0433\u0440\u043e\u043a\u0438 \u0440\u044f\u0434\u043e\u043c
        if (safeMode && runTicks % 10 == 0) {
            for (Player other : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(PLAYER_RADIUS))) {
                if (other == player || !other.isAlive()) continue;
                disable(mc, "\u00a7c\u0420\u044f\u0434\u043e\u043c \u0438\u0433\u0440\u043e\u043a " + other.getName().getString() + ", \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
                return;
            }
        }

        // --- \u043c\u043e\u0431\u044b
        Monster nearest = null;
        double nearestDist = ENGAGE_RANGE * ENGAGE_RANGE;
        boolean monsterNear = false;
        for (Monster m : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(32.0d))) {
            if (!m.isAlive()) continue;
            if (m.getType() == EntityType.WARDEN) {
                disable(mc, "\u00a74\u0420\u044f\u0434\u043e\u043c \u0412\u0430\u0440\u0434\u0435\u043d! \u0410\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d, \u0443\u0445\u043e\u0434\u0438\u0442\u0435 \u0442\u0438\u0445\u043e");
                return;
            }
            double d = m.distanceToSqr(player);
            if (d <= 400.0d) monsterNear = true;
            if (m.getType() == EntityType.ENDERMAN) continue;
            if (d < nearestDist) {
                nearestDist = d;
                nearest = m;
            }
        }
        if (player.hurtTime > 0 && !monsterNear) {
            disable(mc, "\u00a7c\u041f\u043e\u043b\u0443\u0447\u0435\u043d \u0443\u0440\u043e\u043d \u043e\u0442 \u043d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u043e\u0433\u043e \u0438\u0441\u0442\u043e\u0447\u043d\u0438\u043a\u0430, \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
            return;
        }
        if (nearest != null) {
            dropPhase = 0;
            status = "\u0431\u043e\u0439";
            fight(mc, nearest);
            return;
        }

        // --- \u0441\u0442\u0430\u0442\u0438\u0441\u0442\u0438\u043a\u0430: \u0431\u043b\u043e\u043a/\u0440\u0443\u0434\u0430 \u0441\u043b\u043e\u043c\u0430\u043d\u044b
        if (mineTarget != null && level.getBlockState(mineTarget).isAir()) {
            blocksDug++;
            mineTarget = null;
        }
        if (oreTarget != null && level.getBlockState(oreTarget).isAir()) {
            oresMined++;
            pickupTicks = PICKUP_WINDOW;
            oreTarget = null;
        }

        if (handleEating(mc)) return;
        if (handleJunk(mc)) return;
        if (handlePickup(mc)) return;

        // --- \u043d\u0430\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435 \u0432\u0437\u0433\u043b\u044f\u0434\u0430 \u043f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e
        player.setYRot(currentDirection.toYRot());
        player.setXRot(0.0f);

        // --- \u043f\u0440\u044f\u043c\u043e\u0439 \u0443\u0447\u0430\u0441\u0442\u043e\u043a / \u0437\u0430\u0441\u0442\u0440\u0435\u0432\u0430\u043d\u0438\u0435
        if (lastPos != null && lastPos.getY() == pos.getY()
                && (lastPos.getX() != pos.getX() || lastPos.getZ() != pos.getZ())) {
            blocksDugInStraightLine++;
        }
        lastPos = pos;

        if (stuckPos != null && stuckPos.equals(pos)) {
            stuckTicks++;
        } else {
            stuckPos = pos;
            stuckTicks = 0;
        }
        if (stuckTicks > STUCK_TICKS) {
            disable(mc, "\u00a7c\u0411\u043e\u0442 \u0437\u0430\u0441\u0442\u0440\u044f\u043b \u043d\u0430 \u043e\u0434\u043d\u043e\u043c \u043c\u0435\u0441\u0442\u0435, \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
            return;
        }

        // --- \u043f\u043e \u0432\u044b\u0441\u043e\u0442\u0435
        int y = pos.getY();
        if (y < MIN_Y) {
            disable(mc, "\u00a7c\u0412\u044b \u043d\u0438\u0436\u0435 \u0440\u0430\u0431\u043e\u0447\u0435\u0439 \u0432\u044b\u0441\u043e\u0442\u044b Y=-59, \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
            return;
        }
        if (y > targetY) {
            digDown(mc, pos);
            return;
        }
        if (y < targetY) {
            climb(mc, pos);
            return;
        }

        tunnel(mc, pos);
    }

    // ================================================================== \u0442\u0443\u043d\u043d\u0435\u043b\u044c

    private static void tunnel(Minecraft mc, BlockPos pos) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        BlockPos front = pos.relative(currentDirection);
        BlockPos head = front.above();

        if (!isPathSafe(level, pos, currentDirection)) {
            if (!turnToSafeDirection(level, pos)) {
                disable(mc, "\u00a7c\u041e\u043f\u0430\u0441\u043d\u043e\u0441\u0442\u044c \u0441\u043e \u0432\u0441\u0435\u0445 \u0441\u0442\u043e\u0440\u043e\u043d (\u043b\u0430\u0432\u0430/\u0433\u0440\u0430\u0432\u0438\u0439/\u0431\u0435\u0434\u0440\u043e\u043a), \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
            }
            return;
        }

        // \u0440\u0443\u0434\u0430 \u0432 \u043f\u0440\u0438\u043e\u0440\u0438\u0442\u0435\u0442\u0435: \u0441\u043d\u0430\u0447\u0430\u043b\u0430 \u0434\u043e\u0431\u044b\u0432\u0430\u0435\u043c \u0438 \u043f\u043e\u0434\u0431\u0438\u0440\u0430\u0435\u043c, \u0438 \u0442\u043e\u043b\u044c\u043a\u043e \u043f\u043e\u0442\u043e\u043c \u043f\u043e\u0432\u043e\u0440\u0430\u0447\u0438\u0432\u0430\u0435\u043c
        BlockPos ore = findVisibleOre(level, player, pos);
        if (ore != null && !hasLavaAround(level, ore) && tryMine(mc, ore)) {
            status = "\u043a\u043e\u043f\u0430\u044e \u0430\u043b\u043c\u0430\u0437\u043d\u0443\u044e \u0440\u0443\u0434\u0443";
            return;
        }

        if (blocksDugInStraightLine > STRAIGHT_MIN + RANDOM.nextInt(STRAIGHT_RANDOM)) {
            turnToSafeDirection(level, pos);
            blocksDugInStraightLine = 0;
            maybeChangeHeight(mc);
            return;
        }

        BlockState headState = level.getBlockState(head);
        BlockState frontState = level.getBlockState(front);

        if (headState.is(Blocks.BEDROCK) || frontState.is(Blocks.BEDROCK)) {
            if (!turnToSafeDirection(level, pos)) {
                disable(mc, "\u00a7c\u0422\u0443\u043f\u0438\u043a, \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
            }
            return;
        }

        if (isMineable(headState)) {
            status = "\u043a\u043e\u043f\u0430\u044e \u0442\u0443\u043d\u043d\u0435\u043b\u044c";
            if (!tryMine(mc, head) && isTargetOre(headState)) turnToSafeDirection(level, pos);
        } else if (isMineable(frontState)) {
            status = "\u043a\u043e\u043f\u0430\u044e \u0442\u0443\u043d\u043d\u0435\u043b\u044c";
            if (!tryMine(mc, front) && isTargetOre(frontState)) turnToSafeDirection(level, pos);
        } else {
            BlockState floor = level.getBlockState(front.below());
            if (floor.isAir() || !floor.getFluidState().isEmpty()) {
                if (!turnToSafeDirection(level, pos)) {
                    disable(mc, "\u00a7c\u0422\u0443\u043f\u0438\u043a, \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
                }
                return;
            }
            walkForward(mc, pos, front);
        }
    }

    // ================================================================== \u0445\u043e\u0434\u044c\u0431\u0430

    private static void trackPos(BlockPos pos) {
        if (!pos.equals(walkPos)) {
            walkPos = pos;
            walkStall = 0;
            centerTicks = 0;
        } else {
            walkStall++;
        }
    }

    /**
     * \u0412\u0441\u0442\u0430\u0451\u043c \u043f\u043e \u0446\u0435\u043d\u0442\u0440\u0443 \u043a\u043b\u0435\u0442\u043a\u0438, \u0435\u0441\u043b\u0438 \u0441\u0442\u043e\u0438\u043c \u0443 \u043a\u0440\u0430\u044f (\u0445\u0438\u0442\u0431\u043e\u043a\u0441 0.6 \u0446\u0435\u043f\u043b\u044f\u0435\u0442\u0441\u044f \u0437\u0430 \u0431\u043b\u043e\u043a\u0438 \u0441\u0431\u043e\u043a\u0443/\u043d\u0430\u0434 \u0433\u043e\u043b\u043e\u0432\u043e\u0439).
     * @return true, \u0435\u0441\u043b\u0438 \u0432 \u044d\u0442\u043e\u043c \u0442\u0438\u043a\u0435 \u0437\u0430\u043d\u0438\u043c\u0430\u043b\u0438\u0441\u044c \u0446\u0435\u043d\u0442\u0440\u043e\u0432\u043a\u043e\u0439
     */
    private static boolean centerInTile(Minecraft mc, BlockPos pos, boolean bothAxes) {
        LocalPlayer player = mc.player;
        double cx = pos.getX() + 0.5d;
        double cz = pos.getZ() + 0.5d;
        boolean alongX = currentDirection == Direction.EAST || currentDirection == Direction.WEST;
        double lateral = alongX ? Math.abs(player.getZ() - cz) : Math.abs(player.getX() - cx);
        double forward = alongX ? Math.abs(player.getX() - cx) : Math.abs(player.getZ() - cz);

        boolean off = lateral > 0.15d || (bothAxes && forward > 0.15d);
        if (!off || centerTicks >= 20) return false;

        centerTicks++;
        status = "\u0446\u0435\u043d\u0442\u0440\u0443\u044e\u0441\u044c";
        double dx = cx - player.getX();
        double dz = cz - player.getZ();
        if (dx * dx + dz * dz > 0.01d) {
            player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0d));
            player.setXRot(0.0f);
            mc.options.keyUp.setDown(true);
        }
        return true;
    }

    private static void walkForward(Minecraft mc, BlockPos pos, BlockPos front) {
        trackPos(pos);
        if (centerInTile(mc, pos, false)) return;

        // \u0432\u0441\u0451 \u0440\u0430\u0432\u043d\u043e \u043d\u0435 \u0438\u0434\u0451\u043c \u2014 \u0437\u043d\u0430\u0447\u0438\u0442 \u0447\u0442\u043e-\u0442\u043e \u0441\u0431\u043e\u043a\u0443 \u0434\u0435\u0440\u0436\u0438\u0442, \u0432\u044b\u043b\u0430\u043c\u044b\u0432\u0430\u0435\u043c \u0431\u043e\u043a\u043e\u0432\u044b\u0435 \u0431\u043b\u043e\u043a\u0438
        if (walkStall >= 25 && clearSides(mc, front)) return;
        if (!enabled) return;

        status = "\u0438\u0434\u0443";
        mc.options.keyUp.setDown(true);
    }

    private static boolean clearSides(Minecraft mc, BlockPos front) {
        ClientLevel level = mc.level;
        Direction cw = currentDirection.getClockWise();
        Direction ccw = currentDirection.getCounterClockWise();
        BlockPos[] candidates = {
                front.relative(cw), front.relative(ccw),
                front.relative(cw).above(), front.relative(ccw).above()
        };
        for (BlockPos c : candidates) {
            if (IGNORED.contains(c)) continue;
            BlockState s = level.getBlockState(c);
            if (!isMineable(s) || hasLavaAround(level, c)) continue;
            if (tryMine(mc, c)) return true;
        }
        return false;
    }

    // ================================================================== \u0441\u043c\u0435\u043d\u0430 \u0432\u044b\u0441\u043e\u0442\u044b

    private static void maybeChangeHeight(Minecraft mc) {
        if (RANDOM.nextInt(100) >= HEIGHT_CHANGE_PERCENT) return;
        int n = HEIGHT_MIN + RANDOM.nextInt(HEIGHT_MAX - HEIGHT_MIN + 1);
        if (n == targetY) return;
        targetY = n;
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal("\u00a7b\u041c\u0435\u043d\u044f\u044e \u0432\u044b\u0441\u043e\u0442\u0443: Y=" + n));
        }
    }

    /** \u0421\u043f\u0443\u0441\u043a: \u043a\u043e\u043f\u0430\u0435\u043c \u0448\u0430\u0445\u0442\u0443 \u043f\u043e\u0434 \u0441\u043e\u0431\u043e\u0439. */
    private static void digDown(Minecraft mc, BlockPos pos) {
        ClientLevel level = mc.level;
        BlockPos below = pos.below();
        if (hasLavaAround(level, below) || hasLavaAround(level, below.below())) {
            // \u043b\u0430\u0432\u0430 \u0432\u043d\u0438\u0437\u0443 \u2014 \u043e\u0441\u0442\u0430\u0451\u043c\u0441\u044f \u043d\u0430 \u044d\u0442\u043e\u0439 \u0432\u044b\u0441\u043e\u0442\u0435, \u0435\u0441\u043b\u0438 \u043e\u043d\u0430 \u0434\u043e\u043f\u0443\u0441\u0442\u0438\u043c\u0430
            if (pos.getY() >= MIN_Y && pos.getY() <= HEIGHT_MAX) {
                targetY = pos.getY();
                return;
            }
            disable(mc, "\u00a7c\u041b\u0430\u0432\u0430 \u0432\u043d\u0438\u0437\u0443, \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
            return;
        }
        status = "\u043a\u043e\u043f\u0430\u044e \u0432\u043d\u0438\u0437";
        tryMine(mc, below);
    }

    /**
     * \u041f\u043e\u0434\u044a\u0451\u043c \u043b\u0435\u0441\u0435\u043d\u043a\u043e\u0439 1:1. \u0427\u0438\u0441\u0442\u0438\u043c 3 \u043a\u043b\u0435\u0442\u043a\u0438 \u043d\u0430\u0434 \u0441\u043e\u0431\u043e\u0439 \u0438 3 \u043a\u043b\u0435\u0442\u043a\u0438 \u0432\u043f\u0435\u0440\u0435\u0434\u0438-\u0432\u044b\u0448\u0435, \u0432\u0441\u0442\u0430\u0451\u043c \u043f\u043e \u0446\u0435\u043d\u0442\u0440\u0443
     * \u0438 \u0442\u043e\u043b\u044c\u043a\u043e \u043f\u043e\u0442\u043e\u043c \u043f\u0440\u044b\u0433\u0430\u0435\u043c \u2014 \u0438\u043d\u0430\u0447\u0435 \u0445\u0438\u0442\u0431\u043e\u043a\u0441 \u0446\u0435\u043f\u043b\u044f\u0435\u0442\u0441\u044f \u0433\u043e\u043b\u043e\u0432\u043e\u0439 \u0437\u0430 \u043f\u043e\u0442\u043e\u043b\u043e\u043a.
     */
    private static void climb(Minecraft mc, BlockPos pos) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        BlockPos front = pos.relative(currentDirection);
        BlockPos[] clear = {
                pos.above(2), pos.above(3),
                front.above(2), front.above(3),
                front.above()
        };

        // \u043d\u0435\u0431\u0435\u0437\u043e\u043f\u0430\u0441\u043d\u043e \u043f\u043e\u0434\u043d\u0438\u043c\u0430\u0442\u044c\u0441\u044f (\u043b\u0430\u0432\u0430 / \u0441\u044b\u043f\u0443\u0447\u0438\u0439 \u0431\u043b\u043e\u043a \u043d\u0430\u0434 \u0433\u043e\u043b\u043e\u0432\u043e\u0439) \u2014 \u043e\u0441\u0442\u0430\u0451\u043c\u0441\u044f \u043d\u0430 \u044d\u0442\u043e\u0439 \u0432\u044b\u0441\u043e\u0442\u0435
        boolean unsafe = !isPathSafe(level, pos, currentDirection);
        for (BlockPos c : clear) {
            if (hasLavaAround(level, c)
                    || level.getBlockState(c).getBlock() instanceof FallingBlock
                    || level.getBlockState(c.above()).getBlock() instanceof FallingBlock) {
                unsafe = true;
            }
        }
        if (unsafe) {
            targetY = pos.getY();
            return;
        }

        BlockPos ore = findVisibleOre(level, player, pos);
        if (ore != null && !hasLavaAround(level, ore) && tryMine(mc, ore)) {
            status = "\u043a\u043e\u043f\u0430\u044e \u0430\u043b\u043c\u0430\u0437\u043d\u0443\u044e \u0440\u0443\u0434\u0443";
            return;
        }

        for (BlockPos c : clear) {
            BlockState s = level.getBlockState(c);
            if (s.is(Blocks.BEDROCK)) {
                targetY = pos.getY();
                return;
            }
            if (isMineable(s)) {
                status = "\u043a\u043e\u043f\u0430\u044e \u043b\u0435\u0441\u0435\u043d\u043a\u0443";
                if (!tryMine(mc, c) && isTargetOre(s)) {
                    targetY = pos.getY();
                }
                return;
            }
        }

        trackPos(pos);
        if (centerInTile(mc, pos, true)) return;

        status = "\u043f\u043e\u0434\u043d\u0438\u043c\u0430\u044e\u0441\u044c";
        mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown(true);
    }

    // ================================================================== \u0431\u043e\u0439

    private static void fight(Minecraft mc, Monster target) {
        LocalPlayer player = mc.player;
        selectWeapon(player.getInventory());
        aimAt(player, target.getBoundingBox().getCenter());
        if (player.getAttackStrengthScale(0.5f) >= 0.95f) {
            mc.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void selectWeapon(Inventory inv) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = inv.getItem(i);
                boolean ok = pass == 0 ? s.is(ItemTags.SWORDS) : s.is(ItemTags.AXES);
                if (ok && !isAlmostBroken(s)) {
                    inv.setSelectedSlot(i);
                    return;
                }
            }
        }
    }

    // ================================================================== \u0435\u0434\u0430 / \u043c\u0443\u0441\u043e\u0440

    private static boolean handleEating(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player.isUsingItem()) {
            status = "\u0435\u043c";
            mc.options.keyUse.setDown(true);
            return true;
        }
        if (player.getFoodData().getFoodLevel() <= EAT_BELOW) {
            Inventory inv = player.getInventory();
            for (int i = 0; i < 9; i++) {
                if (FOOD.contains(inv.getItem(i).getItem())) {
                    inv.setSelectedSlot(i);
                    mc.options.keyUse.setDown(true);
                    status = "\u0435\u043c";
                    return true;
                }
            }
            warn(mc, "\u00a7c\u041d\u0435\u0442 \u0435\u0434\u044b \u0432 \u0445\u043e\u0442\u0431\u0430\u0440\u0435");
        }
        return false;
    }

    private static boolean handleJunk(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (dropPhase > 0) {
            status = "\u0432\u044b\u0431\u0440\u0430\u0441\u044b\u0432\u0430\u044e \u043c\u0443\u0441\u043e\u0440";
            player.setYRot(currentDirection.getOpposite().toYRot());
            player.setXRot(0.0f);
            dropPhase++;
            if (dropPhase >= 4) {
                dropJunk(mc);
                dropPhase = 0;
                junkCooldown = 100;
            }
            return true;
        }
        Inventory inv = player.getInventory();
        int junk = 0;
        boolean full = true;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                full = false;
            } else if (JUNK.contains(s.getItem())) {
                junk++;
            }
        }
        if (full && junk == 0) {
            warn(mc, "\u00a7c\u0418\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c \u043f\u043e\u043b\u043e\u043d");
            if (++fullTicks > FULL_INVENTORY_TICKS) {
                disable(mc, "\u00a7c\u0418\u043d\u0432\u0435\u043d\u0442\u0430\u0440\u044c \u043f\u043e\u043b\u043e\u043d, \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
                return true;
            }
        } else {
            fullTicks = 0;
        }
        if (junkCooldown <= 0 && junk >= JUNK_STACKS_TRIGGER) {
            dropPhase = 1;
            return true;
        }
        return false;
    }

    private static void dropJunk(Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        int dropped = 0;
        for (int i = 0; i < 36 && dropped < MAX_DROPS_PER_RUN; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && JUNK.contains(s.getItem())) {
                dropStack(mc, i < 9 ? 36 + i : i);
                dropped++;
            }
        }
    }

    private static void dropStack(Minecraft mc, int slot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 1,
                ContainerInput.THROW, mc.player);
    }

    // ================================================================== \u0434\u043e\u0431\u044b\u0447\u0430

    /**
     * \u041b\u043e\u043c\u0430\u0435\u0442 \u0431\u043b\u043e\u043a, \u0443\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u044f \u043d\u0430\u0441\u0442\u043e\u044f\u0449\u0443\u044e \u043a\u043d\u043e\u043f\u043a\u0443 \u0430\u0442\u0430\u043a\u0438 \u0438 \u043d\u0430\u0432\u043e\u0434\u044f \u043d\u0430 \u0431\u043b\u043e\u043a \u043f\u0440\u0438\u0446\u0435\u043b
     * (\u0432\u0430\u043d\u0438\u043b\u044c \u0441\u0431\u0440\u0430\u0441\u044b\u0432\u0430\u0435\u0442 \u043f\u0440\u043e\u0433\u0440\u0435\u0441\u0441 \u043b\u043e\u043c\u0430\u043d\u0438\u044f \u043a\u0430\u0436\u0434\u044b\u0439 \u0442\u0438\u043a, \u0435\u0441\u043b\u0438 \u041b\u041a\u041c \u043d\u0435 \u0437\u0430\u0436\u0430\u0442\u0430).
     *
     * @return true \u2014 \u0440\u0430\u0431\u043e\u0442\u0430\u0435\u043c \u043d\u0430\u0434 \u0431\u043b\u043e\u043a\u043e\u043c (\u043d\u0430\u0432\u043e\u0434\u0438\u043c\u0441\u044f/\u043b\u043e\u043c\u0430\u0435\u043c); false \u2014 \u043d\u0435 \u043c\u043e\u0436\u0435\u043c (\u043d\u0435\u0442 \u043a\u0438\u0440\u043a\u0438 \u0438 \u0442.\u043f.)
     */
    private static boolean tryMine(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.BEDROCK)) return false;

        boolean ore = isTargetOre(state);
        LocalPlayer player = mc.player;
        if (!preparePickaxe(player.getInventory(), ore)) {
            warn(mc, ore
                    ? "\u00a7c\u041d\u0435\u0442 \u043a\u0438\u0440\u043a\u0438 \u043d\u0430 \u0428\u0451\u043b\u043a (\u0438\u043b\u0438 \u043f\u0440\u043e\u0447\u043d\u043e\u0441\u0442\u044c <= 10)"
                    : "\u00a7c\u041d\u0435\u0442 \u043a\u0438\u0440\u043a\u0438 \u043d\u0430 \u042d\u0444\u0444\u0435\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c (\u0438\u043b\u0438 \u043f\u0440\u043e\u0447\u043d\u043e\u0441\u0442\u044c <= 10)");
            if (!ore && ++noToolTicks > NO_TOOL_TICKS) {
                disable(mc, "\u00a7c\u041d\u0435\u0442 \u0440\u0430\u0431\u043e\u0447\u0435\u0439 \u043a\u0438\u0440\u043a\u0438, \u0430\u0432\u0442\u043e-\u043c\u0430\u0439\u043d\u0435\u0440 \u0432\u044b\u043a\u043b\u044e\u0447\u0435\u043d");
            }
            return false;
        }
        if (!ore) noToolTicks = 0;

        if (!pos.equals(mineTarget)) {
            mineTarget = pos;
            aimMiss = 0;
            stuckTicks = 0; // \u043d\u043e\u0432\u044b\u0439 \u0431\u043b\u043e\u043a \u0432 \u0440\u0430\u0431\u043e\u0442\u0435 \u2014 \u044d\u0442\u043e \u043f\u0440\u043e\u0433\u0440\u0435\u0441\u0441, \u0430 \u043d\u0435 \u00ab\u0437\u0430\u0441\u0442\u0440\u044f\u043b\u00bb
        }
        if (ore) oreTarget = pos;

        aimAt(player, Vec3.atCenterOf(pos));

        if (crosshairOn(mc, pos)) {
            aimMiss = 0;
            mc.options.keyAttack.setDown(true);
        } else if (++aimMiss > AIM_TIMEOUT) {
            // \u0434\u043e \u0431\u043b\u043e\u043a\u0430 \u043d\u0435 \u0434\u043e\u0442\u044f\u043d\u0443\u0442\u044c\u0441\u044f \u043f\u0440\u0438\u0446\u0435\u043b\u043e\u043c (\u0437\u0430\u043a\u0440\u044b\u0442 \u0434\u0440\u0443\u0433\u0438\u043c \u0431\u043b\u043e\u043a\u043e\u043c)
            IGNORED.add(pos);
            if (!ore) blocksDugInStraightLine = 1000; // \u0437\u0430\u0441\u0442\u0430\u0432\u0438\u043c \u0441\u0432\u0435\u0440\u043d\u0443\u0442\u044c
            aimMiss = 0;
            return false;
        }
        return true;
    }

    private static boolean crosshairOn(Minecraft mc, BlockPos pos) {
        HitResult hit = mc.hitResult;
        return hit instanceof BlockHitResult bhr
                && hit.getType() == HitResult.Type.BLOCK
                && bhr.getBlockPos().equals(pos);
    }

    private static void aimAt(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0d));
        player.setXRot((float) (-Math.toDegrees(Math.atan2(dy, horiz))));
    }

    // ================================================================== \u043f\u043e\u0434\u0431\u043e\u0440 \u0434\u0440\u043e\u043f\u0430

    private static ItemEntity findPickupTarget(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        ItemEntity best = null;
        double bestDist = PICKUP_RADIUS * PICKUP_RADIUS;
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(PICKUP_RADIUS))) {
            if (!it.isAlive() || GIVE_UP.contains(it.getId())) continue;
            if (JUNK.contains(it.getItem().getItem())) continue; // \u0437\u0430 \u0431\u0443\u043b\u044b\u0436\u043d\u0438\u043a\u043e\u043c \u043d\u0435 \u0431\u0435\u0433\u0430\u0435\u043c
            BlockPos ip = it.blockPosition();
            if (hasLavaAround(level, ip) || hasLavaAround(level, ip.below())) continue;
            double d = it.distanceToSqr(player);
            if (d < bestDist) {
                bestDist = d;
                best = it;
            }
        }
        return best;
    }

    private static boolean handlePickup(Minecraft mc) {
        if (pickupTicks <= 0) {
            // \u043f\u0435\u0440\u0438\u043e\u0434\u0438\u0447\u0435\u0441\u043a\u0438 \u043f\u0440\u043e\u0432\u0435\u0440\u044f\u0435\u043c, \u043d\u0435 \u0432\u0430\u043b\u044f\u0435\u0442\u0441\u044f \u043b\u0438 \u0440\u044f\u0434\u043e\u043c \u0447\u0442\u043e-\u0442\u043e \u0446\u0435\u043d\u043d\u043e\u0435
            if (runTicks % 10 != 0 || findPickupTarget(mc) == null) return false;
            pickupTicks = PICKUP_WINDOW;
        }
        pickupTicks--;

        LocalPlayer player = mc.player;
        ItemEntity best = findPickupTarget(mc);
        if (best == null) {
            // \u043f\u0435\u0440\u0432\u044b\u0435 ~15 \u0442\u0438\u043a\u043e\u0432 \u0436\u0434\u0451\u043c, \u043f\u043e\u043a\u0430 \u0434\u0440\u043e\u043f \u043f\u043e\u044f\u0432\u0438\u0442\u0441\u044f; \u043f\u043e\u0442\u043e\u043c \u2014 \u043d\u0435\u0447\u0435\u0433\u043e \u0441\u043e\u0431\u0438\u0440\u0430\u0442\u044c
            if (pickupTicks < PICKUP_WINDOW - 15) pickupTicks = 0;
            return false;
        }
        if (pickupTicks == 0) {
            GIVE_UP.add(best.getId()); // \u043d\u0435 \u0434\u043e\u0448\u043b\u0438 \u0437\u0430 \u043e\u0442\u0432\u0435\u0434\u0451\u043d\u043d\u043e\u0435 \u0432\u0440\u0435\u043c\u044f \u2014 \u0431\u043e\u043b\u044c\u0448\u0435 \u043d\u0435 \u0442\u0440\u043e\u0433\u0430\u0435\u043c
            return false;
        }

        status = "\u043f\u043e\u0434\u0431\u0438\u0440\u0430\u044e \u0434\u0440\u043e\u043f";
        Vec3 t = best.position();
        double dx = t.x - player.getX();
        double dz = t.z - player.getZ();
        player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0d));
        player.setXRot(0.0f);
        if (dx * dx + dz * dz > 0.04d) {
            mc.options.keyUp.setDown(true);
        }
        return true;
    }

    // ================================================================== \u0432\u0441\u043f\u043e\u043c\u043e\u0433\u0430\u0442\u0435\u043b\u044c\u043d\u043e\u0435

    private static boolean isAlmostBroken(ItemStack s) {
        return s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= SAFE_DURABILITY;
    }

    private static boolean preparePickaxe(Inventory inv, boolean forOre) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(ItemTags.PICKAXES) || isAlmostBroken(s)) continue;
            boolean silk = s.getEnchantments().keySet().stream().anyMatch(h -> h.is(Enchantments.SILK_TOUCH));
            if (silk == forOre) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    private static boolean isTargetOre(BlockState s) {
        return TARGET_ORES.contains(s.getBlock());
    }

    private static boolean isMineable(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(Blocks.BEDROCK);
    }

    /** \u0411\u043b\u0438\u0436\u0430\u0439\u0448\u0430\u044f \u0412\u0418\u0414\u0418\u041c\u0410\u042f \u0440\u0443\u0434\u0430 (\u0441 \u0432\u043e\u0437\u0434\u0443\u0445\u043e\u043c \u0440\u044f\u0434\u043e\u043c) \u0432 \u043f\u0440\u0435\u0434\u0435\u043b\u0430\u0445 \u0434\u043e\u0441\u044f\u0433\u0430\u0435\u043c\u043e\u0441\u0442\u0438. \u0421\u043a\u0440\u044b\u0442\u044b\u0435 \u0440\u0443\u0434\u044b \u043d\u0435 \u0442\u0440\u043e\u0433\u0430\u0435\u043c. */
    private static BlockPos findVisibleOre(ClientLevel level, LocalPlayer player, BlockPos origin) {
        Vec3 eye = player.getEyePosition();
        BlockPos best = null;
        double bestDist = ORE_REACH * ORE_REACH;
        for (int dx = -ORE_SCAN; dx <= ORE_SCAN; dx++) {
            for (int dy = -2; dy <= ORE_SCAN; dy++) {
                for (int dz = -ORE_SCAN; dz <= ORE_SCAN; dz++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (IGNORED.contains(p) || !isTargetOre(level.getBlockState(p))) continue;
                    double d = Vec3.atCenterOf(p).distanceToSqr(eye);
                    if (d <= bestDist && hasAirNeighbor(level, p)) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private static boolean hasAirNeighbor(ClientLevel level, BlockPos p) {
        for (Direction d : Direction.values()) {
            if (level.getBlockState(p.relative(d)).isAir()) return true;
        }
        return false;
    }

    private static boolean isLava(ClientLevel level, BlockPos p) {
        return level.getBlockState(p).getFluidState().is(FluidTags.LAVA);
    }

    private static boolean hasLavaAround(ClientLevel level, BlockPos p) {
        if (isLava(level, p)) return true;
        for (Direction d : Direction.values()) {
            if (isLava(level, p.relative(d))) return true;
        }
        return false;
    }

    private static boolean isPathSafe(ClientLevel level, BlockPos pos, Direction dir) {
        Direction cw = dir.getClockWise();
        for (int d = 1; d <= LAVA_LOOKAHEAD; d++) {
            BlockPos base = pos.relative(dir, d);
            for (int dy = -1; dy <= 2; dy++) {
                for (int s = -LAVA_SIDE; s <= LAVA_SIDE; s++) {
                    if (isLava(level, base.relative(cw, s).offset(0, dy, 0))) return false;
                }
            }
        }
        for (int d = 1; d <= FALLING_LOOKAHEAD; d++) {
            BlockPos base = pos.relative(dir, d);
            for (int dy = 0; dy <= 2; dy++) {
                if (level.getBlockState(base.offset(0, dy, 0)).getBlock() instanceof FallingBlock) return false;
            }
        }
        for (int d = 1; d <= 2; d++) {
            BlockPos base = pos.relative(dir, d);
            for (int dy = 0; dy <= 1; dy++) {
                if (level.getBlockState(base.offset(0, dy, 0)).is(Blocks.BEDROCK)) return false;
            }
        }
        return true;
    }

    private static boolean turnToSafeDirection(ClientLevel level, BlockPos pos) {
        Direction cw = currentDirection.getClockWise();
        Direction ccw = currentDirection.getCounterClockWise();
        Direction back = currentDirection.getOpposite();
        Direction[] options = RANDOM.nextBoolean()
                ? new Direction[] {cw, ccw, back}
                : new Direction[] {ccw, cw, back};
        for (Direction d : options) {
            if (isPathSafe(level, pos, d)) {
                currentDirection = d;
                blocksDugInStraightLine = 0;
                centerTicks = 0;
                return true;
            }
        }
        return false;
    }
}
