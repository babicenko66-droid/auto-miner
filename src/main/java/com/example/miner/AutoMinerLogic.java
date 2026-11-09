package com.example.miner;

import java.util.HashSet;
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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AutoMinerLogic {
    // ниже этой высоты бот никогда не опускается (там бедрок)
    private static final int MIN_Y = -59;
    // диапазон высот, между которыми бот иногда переключается
    private static final int HEIGHT_MIN = -59;
    private static final int HEIGHT_MAX = -54;
    // шанс (в %) сменить высоту каждый раз, когда бот заканчивает прямой участок и поворачивает
    private static final int HEIGHT_CHANGE_PERCENT = 40;
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
    private static final boolean SILK_FOR_TUNNEL_IF_NO_EFFICIENCY = false;

    // NEW: сколько тиков ждать, пока прицел встанет на блок, прежде чем сдаться
    private static final int AIM_TIMEOUT = 30;
    // NEW: окно (в тиках) после добытой руды, когда бот собирает выпавшие предметы
    private static final int PICKUP_WINDOW = 60;
    private static final double PICKUP_RADIUS = 6.0d;

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

    // руды, до которых прицел так и не дотянулся (закрыты другим блоком) — пропускаем
    private static final Set<BlockPos> IGNORED = new HashSet<>();

    private static boolean enabled = false;
    private static Direction currentDirection = null;
    private static BlockPos lastPos = null;
    private static BlockPos stuckPos = null;
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

    // ------------------------------------------------------------------ управление

    public static void toggle(Minecraft mc) {
        enabled = !enabled;
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
        IGNORED.clear();
        targetY = HEIGHT_MIN;
        walkPos = null;
        walkStall = 0;
        centerTicks = 0;
        releaseKeys(mc);
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal(enabled
                    ? "§aАвто-майнер включён"
                    : "§eАвто-майнер выключен"));
        }
    }

    private static void disable(Minecraft mc, String reason) {
        enabled = false;
        dropPhase = 0;
        pickupTicks = 0;
        mineTarget = null;
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

    // ------------------------------------------------------------------ главный тик

    public static void tick(Minecraft mc) {
        if (!enabled) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        releaseKeys(mc);
        if (messageCooldown > 0) messageCooldown--;
        if (junkCooldown > 0) junkCooldown--;
        if (mc.screen != null) return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        BlockPos pos = player.blockPosition();

        if (currentDirection == null) currentDirection = player.getDirection();

        if (player.getHealth() < MIN_HEALTH) {
            disable(mc, "§cМало здоровья, авто-майнер выключен");
            return;
        }
        if (player.isOnFire()) {
            disable(mc, "§cВы горите (лава/огонь), авто-майнер выключен");
            return;
        }

        // --- мобы
        Monster nearest = null;
        double nearestDist = ENGAGE_RANGE * ENGAGE_RANGE;
        boolean monsterNear = false;
        for (Monster m : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(32.0d))) {
            if (!m.isAlive()) continue;
            if (m.getType() == EntityType.WARDEN) {
                disable(mc, "§4Рядом Варден! Авто-майнер выключен, уходите тихо");
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
            disable(mc, "§cПолучен урон от неизвестного источника, авто-майнер выключен");
            return;
        }
        if (nearest != null) {
            dropPhase = 0;
            fight(mc, nearest);
            return;
        }

        if (handleEating(mc)) return;
        if (handleJunk(mc)) return;

        // NEW: руда сломана -> включаем окно сбора дропа
        if (oreTarget != null && level.getBlockState(oreTarget).isAir()) {
            pickupTicks = PICKUP_WINDOW;
            oreTarget = null;
            mineTarget = null;
        }
        if (handlePickup(mc)) return;

        // --- направление взгляда по умолчанию
        player.setYRot(currentDirection.toYRot());
        player.setXRot(0.0f);

        // --- детектор застревания / длины прямой
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
            disable(mc, "§cБот застрял на одном месте, авто-майнер выключен");
            return;
        }

        // --- по высоте
        int y = pos.getY();
        if (y < MIN_Y) {
            disable(mc, "§cВы ниже рабочей высоты Y=-59, авто-майнер выключен");
            return;
        }
        if (y > targetY) {
            BlockPos below = pos.below();
            if (hasLavaAround(level, below) || hasLavaAround(level, below.below())) {
                disable(mc, "§cЛава внизу, авто-майнер выключен");
                return;
            }
            tryMine(mc, below);
            return;
        }
        if (y < targetY) {
            climb(mc, pos);
            return;
        }

        // --- прокладываем туннель на высоте targetY
        BlockPos front = pos.relative(currentDirection);
        BlockPos head = front.above();

        if (!isPathSafe(level, pos, currentDirection)) {
            if (!turnToSafeDirection(level, pos)) {
                disable(mc, "§cОпасность со всех сторон (лава/гравий/бедрок), авто-майнер выключен");
            }
            return;
        }

        // руда в приоритете: сначала добываем и подбираем, и только потом поворачиваем
        BlockPos ore = findVisibleOre(level, pos);
        if (ore != null && !hasLavaAround(level, ore) && tryMine(mc, ore)) {
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
                disable(mc, "§cТупик, авто-майнер выключен");
            }
            return;
        }

        if (isMineable(headState)) {
            if (!tryMine(mc, head) && isTargetOre(headState)) turnToSafeDirection(level, pos);
        } else if (isMineable(frontState)) {
            if (!tryMine(mc, front) && isTargetOre(frontState)) turnToSafeDirection(level, pos);
        } else {
            BlockState floor = level.getBlockState(front.below());
            if (floor.isAir() || !floor.getFluidState().isEmpty()) {
                if (!turnToSafeDirection(level, pos)) {
                    disable(mc, "§cТупик, авто-майнер выключен");
                }
                return;
            }
            walkForward(mc, pos, front);
        }
    }

    // ------------------------------------------------------------------ ходьба (ИСПРАВЛЕНО)

    /**
     * Идём вперёд, но сначала встаём по центру клетки.
     *
     * После поворота бот стоит у края клетки, и его хитбокс (0.6) цепляется за
     * нетронутый блок сбоку — он упирается в стену и через 20 сек выключается как «застрявший».
     */
    private static void walkForward(Minecraft mc, BlockPos pos, BlockPos front) {
        LocalPlayer player = mc.player;

        if (!pos.equals(walkPos)) {
            walkPos = pos;
            walkStall = 0;
            centerTicks = 0;
        } else {
            walkStall++;
        }

        // боковое смещение относительно направления движения
        double cx = pos.getX() + 0.5d;
        double cz = pos.getZ() + 0.5d;
        boolean alongX = currentDirection == Direction.EAST || currentDirection == Direction.WEST;
        double lateral = alongX ? Math.abs(player.getZ() - cz) : Math.abs(player.getX() - cx);

        if (lateral > 0.15d && centerTicks < 20) {
            centerTicks++;
            double dx = cx - player.getX();
            double dz = cz - player.getZ();
            if (dx * dx + dz * dz > 0.01d) {
                player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0d));
                player.setXRot(0.0f);
                mc.options.keyUp.setDown(true);
            }
            return;
        }

        // всё равно не идём — значит что-то сбоку держит, выламываем боковые блоки
        if (walkStall >= 25 && clearSides(mc, front)) return;

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

    // ------------------------------------------------------------------ смена высоты (НОВОЕ)

    private static void maybeChangeHeight(Minecraft mc) {
        if (RANDOM.nextInt(100) >= HEIGHT_CHANGE_PERCENT) return;
        int n = HEIGHT_MIN + RANDOM.nextInt(HEIGHT_MAX - HEIGHT_MIN + 1);
        if (n == targetY) return;
        targetY = n;
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal("§bМеняю высоту: Y=" + n));
        }
    }

    /** Подъём лесенкой 1:1: чистим потолок, клетку впереди-выше и прыгаем. */
    private static void climb(Minecraft mc, BlockPos pos) {
        ClientLevel level = mc.level;
        BlockPos front = pos.relative(currentDirection);
        BlockPos step = front.above();
        BlockPos step2 = front.above(2);
        BlockPos roof = pos.above(2);

        // небезопасно подниматься (лава / сыпучий блок над головой) — остаёмся на этой высоте
        if (!isPathSafe(level, pos, currentDirection)
                || hasLavaAround(level, roof) || hasLavaAround(level, step) || hasLavaAround(level, step2)
                || level.getBlockState(roof.above()).getBlock() instanceof FallingBlock
                || level.getBlockState(roof).getBlock() instanceof FallingBlock
                || level.getBlockState(step2).getBlock() instanceof FallingBlock
                || level.getBlockState(step2.above()).getBlock() instanceof FallingBlock) {
            targetY = pos.getY();
            return;
        }

        BlockPos ore = findVisibleOre(level, pos);
        if (ore != null && !hasLavaAround(level, ore) && tryMine(mc, ore)) {
            return;
        }

        for (BlockPos c : new BlockPos[] {roof, step2, step}) {
            BlockState s = level.getBlockState(c);
            if (s.is(Blocks.BEDROCK)) {
                targetY = pos.getY();
                return;
            }
            if (isMineable(s)) {
                if (!tryMine(mc, c) && isTargetOre(s)) {
                    targetY = pos.getY();
                }
                return;
            }
        }

        // всё свободно — шаг вперёд и прыжок
        mc.options.keyUp.setDown(true);
        mc.options.keyJump.setDown(true);
    }

    // ------------------------------------------------------------------ бой

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

    // ------------------------------------------------------------------ еда / мусор

    private static boolean handleEating(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player.isUsingItem()) {
            mc.options.keyUse.setDown(true);
            return true;
        }
        if (player.getFoodData().getFoodLevel() <= EAT_BELOW) {
            Inventory inv = player.getInventory();
            for (int i = 0; i < 9; i++) {
                if (FOOD.contains(inv.getItem(i).getItem())) {
                    inv.setSelectedSlot(i);
                    mc.options.keyUse.setDown(true);
                    return true;
                }
            }
            warn(mc, "§cНет еды в хотбаре");
        }
        return false;
    }

    private static boolean handleJunk(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (dropPhase > 0) {
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
            warn(mc, "§cИнвентарь полон");
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

    // ------------------------------------------------------------------ добыча (ИСПРАВЛЕНО)

    /**
     * Ломает блок, удерживая настоящую кнопку атаки и наводя на блок прицел.
     *
     * Старая версия вызывала gameMode.continueDestroyBlock() напрямую раз в тик.
     * Но ваниль в начале каждого тика, пока ЛКМ не зажата, вызывает
     * gameMode.stopDestroyBlock() — прогресс ломания обнулялся каждый тик,
     * и блок, который не ломается за 1 тик (глубинный сланец, руда), не ломался никогда.
     *
     * @return true — работаем над блоком (наводимся/ломаем); false — не можем (нет кирки и т.п.)
     */
    private static boolean tryMine(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.BEDROCK)) return false;

        boolean ore = isTargetOre(state);
        LocalPlayer player = mc.player;
        if (!preparePickaxe(player.getInventory(), ore)) {
            warn(mc, ore
                    ? "§cНет кирки на Шёлк (или прочность <= 10)"
                    : "§cНет кирки на Эффективность (или прочность <= 10)");
            return false;
        }

        if (!pos.equals(mineTarget)) {
            mineTarget = pos;
            aimMiss = 0;
            stuckTicks = 0; // новый блок в работе — это прогресс, а не «застрял»
        }
        if (ore) oreTarget = pos;

        aimAt(player, Vec3.atCenterOf(pos));

        if (crosshairOn(mc, pos)) {
            aimMiss = 0;
            mc.options.keyAttack.setDown(true); // ванильный цикл сам накопит прогресс
        } else if (++aimMiss > AIM_TIMEOUT) {
            // до блока не дотянуться прицелом (закрыт другим блоком)
            IGNORED.add(pos);
            if (!ore) blocksDugInStraightLine = 1000; // заставим свернуть
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

    // ------------------------------------------------------------------ подбор дропа (НОВОЕ)

    private static boolean handlePickup(Minecraft mc) {
        if (pickupTicks <= 0) return false;
        pickupTicks--;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        ItemEntity best = null;
        double bestDist = PICKUP_RADIUS * PICKUP_RADIUS;
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(PICKUP_RADIUS))) {
            if (!it.isAlive()) continue;
            if (JUNK.contains(it.getItem().getItem())) continue; // за булыжником не бегаем
            BlockPos ip = it.blockPosition();
            if (hasLavaAround(level, ip) || hasLavaAround(level, ip.below())) continue;
            double d = it.distanceToSqr(player);
            if (d < bestDist) {
                bestDist = d;
                best = it;
            }
        }

        if (best == null) {
            // первые ~15 тиков ждём, пока дроп появится; потом — нечего собирать
            if (pickupTicks < PICKUP_WINDOW - 15) pickupTicks = 0;
            return false;
        }

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

    // ------------------------------------------------------------------ вспомогательное

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

    private static BlockPos findVisibleOre(ClientLevel level, BlockPos origin) {
        int r = 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (isTargetOre(level.getBlockState(p)) && !IGNORED.contains(p) && hasAirNeighbor(level, p)) {
                        return p;
                    }
                }
            }
        }
        return null;
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
                return true;
            }
        }
        return false;
    }
}
