package com.example.miner;

import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AutoMinerLogic {
    // ===== Настройки =====
    private static final int TARGET_Y = -59;            // рабочая высота (Y ног игрока)
    private static final int SAFE_DURABILITY = 10;      // предметы с остатком прочности <= 10 не используются
    private static final int LAVA_LOOKAHEAD = 6;        // на сколько блоков вперёд искать лаву
    private static final int LAVA_SIDE = 2;             // и на сколько блоков в стороны
    private static final int FALLING_LOOKAHEAD = 3;     // гравий/песок над будущим туннелем
    private static final int STRAIGHT_MIN = 30;         // смена направления каждые 30...
    private static final int STRAIGHT_RANDOM = 20;      // ...50 блоков
    private static final float MIN_HEALTH = 8.0f;       // ниже 4 сердец майнер выключается
    private static final int EAT_BELOW = 14;            // есть, когда голод <= 14 (7 "ножек")
    private static final double ENGAGE_RANGE = 3.5;     // бить мобов в этом радиусе
    private static final int JUNK_STACKS_TRIGGER = 3;   // выбрасывать мусор, когда накопилось столько стаков
    private static final int MAX_DROPS_PER_RUN = 4;     // стаков за один раз (чтобы не спамить сервер)
    private static final int STUCK_TICKS = 400;         // 20 секунд на одном месте = застрял
    private static final boolean SILK_FOR_TUNNEL_IF_NO_EFFICIENCY = false;

    // Руды, которые копаются ТОЛЬКО Шёлковым касанием. Всё остальное при туннелировании ломается Эффективностью.
    private static final Set<Block> TARGET_ORES = Set.of(
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE
    );

    // Что можно есть (только из хотбара)
    private static final Set<Item> FOOD = Set.of(
            Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN, Items.COOKED_MUTTON,
            Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BREAD,
            Items.BAKED_POTATO, Items.GOLDEN_CARROT, Items.CARROT, Items.APPLE, Items.PUMPKIN_PIE
    );

    // Что выбрасывается как мусор (руды, кирки, меч, еда сюда не входят)
    private static final Set<Item> JUNK = Set.of(
            Blocks.COBBLESTONE.asItem(), Blocks.COBBLED_DEEPSLATE.asItem(), Blocks.STONE.asItem(),
            Blocks.DEEPSLATE.asItem(), Blocks.TUFF.asItem(), Blocks.DIRT.asItem(), Blocks.GRAVEL.asItem(),
            Blocks.ANDESITE.asItem(), Blocks.DIORITE.asItem(), Blocks.GRANITE.asItem(), Blocks.CALCITE.asItem(),
            Items.FLINT, Items.ROTTEN_FLESH, Items.BONE, Items.SPIDER_EYE, Items.ARROW
    );

    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean enabled = false;
    private static Direction currentDirection = null;
    private static BlockPos lastPos = null;
    private static BlockPos stuckPos = null;
    private static int stuckTicks = 0;
    private static int blocksDugInStraightLine = 0;
    private static int messageCooldown = 0;
    private static int junkCooldown = 0;
    private static int dropPhase = 0;

    // ===== Управление =====
    public static void toggle(Minecraft client) {
        enabled = !enabled;
        currentDirection = null;
        lastPos = null;
        stuckPos = null;
        stuckTicks = 0;
        blocksDugInStraightLine = 0;
        dropPhase = 0;
        releaseKeys(client);
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(enabled ? "§aАвто-майнер включён" : "§eАвто-майнер выключен"), true);
        }
    }

    private static void disable(Minecraft client, String reason) {
        enabled = false;
        dropPhase = 0;
        releaseKeys(client);
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(reason), false);
        }
    }

    private static void releaseKeys(Minecraft client) {
        if (client.options != null) {
            client.options.keyUp.setDown(false);
            client.options.keyUse.setDown(false);
        }
    }

    private static void warn(Minecraft client, String text) {
        if (messageCooldown <= 0 && client.player != null) {
            client.player.displayClientMessage(Component.literal(text), true);
            messageCooldown = 40;
        }
    }

    // ===== Основной цикл =====
    public static void tick(Minecraft client) {
        if (!enabled) return;
        if (client.player == null || client.level == null || client.gameMode == null) return;

        releaseKeys(client);
        if (messageCooldown > 0) messageCooldown--;
        if (junkCooldown > 0) junkCooldown--;
        if (client.screen != null) return; // открыт инвентарь/меню

        var player = client.player;
        ClientLevel level = client.level;
        BlockPos playerPos = player.blockPosition();
        Inventory inventory = player.getInventory();

        if (currentDirection == null) {
            currentDirection = player.getDirection();
        }

        // ----- Безопасность -----
        if (player.getHealth() < MIN_HEALTH) {
            disable(client, "§cМало здоровья, авто-майнер выключен");
            return;
        }
        if (player.isOnFire()) {
            disable(client, "§cВы горите (лава/огонь), авто-майнер выключен");
            return;
        }

        // Сканируем мобов вокруг одним запросом
        List<Monster> monsters = level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(32));
        Monster nearest = null;
        double best = ENGAGE_RANGE * ENGAGE_RANGE;
        boolean anyMobNear = false;
        for (Monster m : monsters) {
            if (!m.isAlive()) continue;
            if (m.getType() == EntityType.WARDEN) {
                disable(client, "§4Рядом Варден! Авто-майнер выключен, уходите тихо");
                return;
            }
            double d = m.distanceToSqr(player);
            if (d <= 400) anyMobNear = true;
            if (m.getType() == EntityType.ENDERMAN) continue; // эндерменов не трогаем
            if (d < best) {
                best = d;
                nearest = m;
            }
        }
        // Урон есть, а мобов рядом нет: лава, удушье, утопление, голод
        if (player.hurtTime > 0 && !anyMobNear) {
            disable(client, "§cПолучен урон от неизвестного источника, авто-майнер выключен");
            return;
        }

        // ----- Бой -----
        if (nearest != null) {
            dropPhase = 0;
            fight(client, nearest);
            return;
        }

        // ----- Еда -----
        if (handleEating(client)) return;

        // ----- Выбрасывание мусора -----
        if (handleJunk(client)) return;

        // Смотрим в сторону движения
        player.setYRot(currentDirection.toYRot());
        player.setXRot(0.0f);

        // Счётчик пройденных блоков по прямой (по реальному перемещению игрока)
        if (lastPos != null && lastPos.getY() == playerPos.getY()
                && (lastPos.getX() != playerPos.getX() || lastPos.getZ() != playerPos.getZ())) {
            blocksDugInStraightLine++;
        }
        lastPos = playerPos;

        // Детектор застревания
        if (stuckPos != null && stuckPos.equals(playerPos)) {
            stuckTicks++;
        } else {
            stuckPos = playerPos;
            stuckTicks = 0;
        }
        if (stuckTicks > STUCK_TICKS) {
            disable(client, "§cБот застрял на одном месте, авто-майнер выключен");
            return;
        }

        // 1. Высота
        int y = playerPos.getY();
        if (y < TARGET_Y) {
            disable(client, "§cВы ниже рабочей высоты Y=" + TARGET_Y + ", авто-майнер выключен");
            return;
        }
        if (y > TARGET_Y) {
            BlockPos below = playerPos.below();
            if (hasLavaAround(level, below) || hasLavaAround(level, below.below())) {
                disable(client, "§cЛава внизу, авто-майнер выключен");
                return;
            }
            tryMine(client, below);
            return;
        }

        BlockPos forwardLower = playerPos.relative(currentDirection);
        BlockPos forwardUpper = forwardLower.above();

        // 2. Лава, сыпучие блоки, бедрок впереди
        if (!isPathSafe(level, playerPos, currentDirection)) {
            if (!turnToSafeDirection(level, playerPos)) {
                disable(client, "§cОпасность со всех сторон (лава/гравий/бедрок), авто-майнер выключен");
            }
            return;
        }

        // 3. Периодическая смена направления
        if (blocksDugInStraightLine > STRAIGHT_MIN + RANDOM.nextInt(STRAIGHT_RANDOM)) {
            turnToSafeDirection(level, playerPos);
            blocksDugInStraightLine = 0;
            return;
        }

        // 4. Видимая алмазная руда: копаем Шёлковым касанием
        BlockPos ore = findVisibleOre(level, playerPos);
        if (ore != null && !hasLavaAround(level, ore)) {
            if (tryMine(client, ore)) return;
            // Кирки на Шёлк нет: сообщение уже показано, продолжаем туннель
        }

        // 5. Туннель 1x2 кайлом на Эффективность
        BlockState upper = level.getBlockState(forwardUpper);
        BlockState lower = level.getBlockState(forwardLower);

        if (upper.is(Blocks.BEDROCK) || lower.is(Blocks.BEDROCK)) {
            if (!turnToSafeDirection(level, playerPos)) disable(client, "§cТупик, авто-майнер выключен");
            return;
        }

        if (isMineable(upper)) {
            tryMine(client, forwardUpper);
        } else if (isMineable(lower)) {
            tryMine(client, forwardLower);
        } else {
            // Впереди пусто: шагаем, если под ногами есть опора
            BlockState floor = level.getBlockState(forwardLower.below());
            if (floor.isAir() || !floor.getFluidState().isEmpty()) {
                if (!turnToSafeDirection(level, playerPos)) disable(client, "§cТупик, авто-майнер выключен");
                return;
            }
            client.options.keyUp.setDown(true);
        }
    }

    // ===== Бой =====
    private static void fight(Minecraft client, Monster target) {
        var player = client.player;
        selectWeapon(player.getInventory());

        Vec3 eye = player.getEyePosition();
        Vec3 t = target.getBoundingBox().getCenter();
        double dx = t.x - eye.x;
        double dy = t.y - eye.y;
        double dz = t.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, h)));

        if (player.getAttackStrengthScale(0.5f) >= 0.95f) {
            client.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    // Меч, иначе топор; кирками мобов не бьём
    private static void selectWeapon(Inventory inventory) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inventory.getItem(i);
                boolean ok = pass == 0 ? stack.is(ItemTags.SWORDS) : stack.is(ItemTags.AXES);
                if (ok && !isAlmostBroken(stack)) {
                    inventory.setSelectedSlot(i);
                    return;
                }
            }
        }
    }

    // ===== Еда =====
    private static boolean handleEating(Minecraft client) {
        var player = client.player;
        if (player.isUsingItem()) {
            client.options.keyUse.setDown(true); // дожёвываем
            return true;
        }
        if (player.getFoodData().getFoodLevel() <= EAT_BELOW) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < 9; i++) {
                if (FOOD.contains(inventory.getItem(i).getItem())) {
                    inventory.setSelectedSlot(i);
                    client.options.keyUse.setDown(true);
                    return true;
                }
            }
            warn(client, "§cНет еды в хотбаре");
        }
        return false;
    }

    // ===== Мусор =====
    private static boolean handleJunk(Minecraft client) {
        var player = client.player;

        if (dropPhase > 0) {
            // Разворачиваемся спиной к туннелю, чтобы выброшенное не подобрать обратно
            player.setYRot(currentDirection.getOpposite().toYRot());
            player.setXRot(0.0f);
            dropPhase++;
            if (dropPhase >= 4) {
                dropJunk(client);
                dropPhase = 0;
                junkCooldown = 100;
            }
            return true;
        }

        Inventory inventory = player.getInventory();
        int junkStacks = 0;
        boolean full = true;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) full = false;
            else if (JUNK.contains(s.getItem())) junkStacks++;
        }
        if (full && junkStacks == 0) warn(client, "§cИнвентарь полон");

        if (junkCooldown <= 0 && junkStacks >= JUNK_STACKS_TRIGGER) {
            dropPhase = 1;
            return true;
        }
        return false;
    }

    private static void dropJunk(Minecraft client) {
        var player = client.player;
        Inventory inventory = player.getInventory();
        int dropped = 0;
        for (int i = 0; i < 36 && dropped < MAX_DROPS_PER_RUN; i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && JUNK.contains(s.getItem())) {
                dropStack(client, i < 9 ? 36 + i : i); // слоты хотбара в меню инвентаря: 36..44
                dropped++;
            }
        }
    }

    // Единственное место, завязанное на API клика по слоту (если не компилируется, править здесь)
    private static void dropStack(Minecraft client, int menuSlot) {
        client.gameMode.handleInventoryMouseClick(
                client.player.inventoryMenu.containerId, menuSlot, 1, ClickType.THROW, client.player);
    }

    // ===== Копание и кирки =====
    private static boolean tryMine(Minecraft client, BlockPos pos) {
        BlockState state = client.level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.BEDROCK)) return false;

        boolean needSilk = isTargetOre(state);
        Inventory inventory = client.player.getInventory();

        if (!preparePickaxe(inventory, needSilk)) {
            if (!needSilk && SILK_FOR_TUNNEL_IF_NO_EFFICIENCY && preparePickaxe(inventory, true)) {
                // копаем туннель Шёлком, раз Эффективности нет
            } else {
                warn(client, needSilk
                        ? "§cНет кирки на Шёлк (или прочность <= " + SAFE_DURABILITY + ")"
                        : "§cНет кирки на Эффективность (или прочность <= " + SAFE_DURABILITY + ")");
                return false;
            }
        }

        client.gameMode.continueDestroyBlock(pos, Direction.UP);
        client.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static boolean isAlmostBroken(ItemStack stack) {
        return stack.isDamageableItem() && (stack.getMaxDamage() - stack.getDamageValue() <= SAFE_DURABILITY);
    }

    // Выбирает в хотбаре подходящую кирку (с Шёлком или без), пропуская почти сломанные
    private static boolean preparePickaxe(Inventory inventory, boolean needSilkTouch) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ItemTags.PICKAXES) || isAlmostBroken(stack)) continue;

            boolean hasSilk = stack.getEnchantments().keySet().stream()
                    .anyMatch(holder -> holder.is(Enchantments.SILK_TOUCH));
            if (hasSilk == needSilkTouch) {
                inventory.setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    private static boolean isTargetOre(BlockState state) {
        return TARGET_ORES.contains(state.getBlock());
    }

    private static boolean isMineable(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(Blocks.BEDROCK);
    }

    // ===== Поиск руды =====
    private static BlockPos findVisibleOre(ClientLevel level, BlockPos playerPos) {
        int r = 2;
        for (int x = -r; x <= r; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = playerPos.offset(x, y, z);
                    if (isTargetOre(level.getBlockState(p)) && hasAirNeighbor(level, p)) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasAirNeighbor(ClientLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).isAir()) return true;
        }
        return false;
    }

    // ===== Опасности =====
    private static boolean isLava(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).getFluidState().is(FluidTags.LAVA);
    }

    // Лава в самом блоке или в любом из 6 соседних
    private static boolean hasLavaAround(ClientLevel level, BlockPos pos) {
        if (isLava(level, pos)) return true;
        for (Direction d : Direction.values()) {
            if (isLava(level, pos.relative(d))) return true;
        }
        return false;
    }

    // Коридор вперёд без лавы, сыпучих блоков над туннелем и бедрока на пути
    private static boolean isPathSafe(ClientLevel level, BlockPos from, Direction dir) {
        Direction side = dir.getClockWise();

        for (int i = 1; i <= LAVA_LOOKAHEAD; i++) {
            BlockPos center = from.relative(dir, i);
            for (int dy = -1; dy <= 2; dy++) {
                for (int s = -LAVA_SIDE; s <= LAVA_SIDE; s++) {
                    if (isLava(level, center.relative(side, s).offset(0, dy, 0))) return false;
                }
            }
        }

        for (int i = 1; i <= FALLING_LOOKAHEAD; i++) {
            BlockPos center = from.relative(dir, i);
            for (int dy = 0; dy <= 2; dy++) {
                if (level.getBlockState(center.offset(0, dy, 0)).getBlock() instanceof FallingBlock) return false;
            }
        }

        for (int i = 1; i <= 2; i++) {
            BlockPos center = from.relative(dir, i);
            for (int dy = 0; dy <= 1; dy++) {
                if (level.getBlockState(center.offset(0, dy, 0)).is(Blocks.BEDROCK)) return false;
            }
        }
        return true;
    }

    // Поворот в безопасную сторону (налево/направо, в крайнем случае назад)
    private static boolean turnToSafeDirection(ClientLevel level, BlockPos from) {
        Direction cw = currentDirection.getClockWise();
        Direction ccw = currentDirection.getCounterClockWise();
        Direction back = currentDirection.getOpposite();
        Direction[] order = RANDOM.nextBoolean()
                ? new Direction[] {cw, ccw, back}
                : new Direction[] {ccw, cw, back};
        for (Direction d : order) {
            if (isPathSafe(level, from, d)) {
                currentDirection = d;
                blocksDugInStraightLine = 0;
                return true;
            }
        }
        return false;
    }
}
