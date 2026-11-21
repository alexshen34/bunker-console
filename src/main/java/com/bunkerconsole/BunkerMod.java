package com.bunkerconsole;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;

import java.util.List;

/**
 * 37号避难所·循环地图核心逻辑
 * 1.每日重置 2.展示框检测 3.红石信号 4.进度计数 5.剧情状态机
 * 6.配置魔杖：右键展示框→输入物品ID→自动配置
 */
public class BunkerMod implements ModInitializer {

    public static final String VERSION = "2.5.0";
    public static Item CONFIGURATOR;
    public static Block GLOW_BLOCK;
    public static Block PORTAL_BLOCK;

    /** 预留关卡维度（数据包预定义，overworld 预设） */
    public static final List<String> LEVEL_DIMS = List.of(
        "minecraft:overworld", "bunkerconsole:level2", "bunkerconsole:level3");

    public static int timer = 6000;
    public static int day = 1;
    public static int correct = 0;
    public static int stage = 0;
    public static boolean resetting = false;
    public static int resetPhase = 0;
    public static boolean doneTriggered = false;
    public static int tickCounter = 0;
    public static int doneTimer = 0;
    public static boolean paused = false;
    public static int dayStartTick = 0; // 一天开始时的tick值（用于判断快速完成）
    public static boolean fastClearUnlocked = false; // 快速完成后解锁所有配方

    // 告示牌内容备份（用NBT存整个方块实体数据）
    public static class SignBackup {
        public BlockPos pos;
        public net.minecraft.nbt.NbtCompound nbt;
    }
    public static List<SignBackup> signBackups = new java.util.ArrayList<>();

    // 展示框实体备份（整个NBT）
    public static class FrameBackup {
        public net.minecraft.nbt.NbtCompound nbt;
    }
    public static List<FrameBackup> frameBackups = new java.util.ArrayList<>();

    // 土豆箱NBT备份
    public static net.minecraft.nbt.NbtCompound potatoChestNbt = null;

    @Override
    public void onInitialize() {
        CONFIGURATOR = Registry.register(Registries.ITEM,
            new Identifier("bunkerconsole", "configurator"),
            new Item(new Item.Settings().maxCount(1)));

        // 注册超高亮度光源方块（亮度20）
        GLOW_BLOCK = Registry.register(Registries.BLOCK,
            new Identifier("bunkerconsole", "glow_block"),
            new GlowBlock(AbstractBlock.Settings.copy(Blocks.GLOWSTONE).luminance(state -> 20)));
        Registry.register(Registries.ITEM,
            new Identifier("bunkerconsole", "glow_block"),
            new BlockItem(GLOW_BLOCK, new Item.Settings()));

        // 注册半透明传送门方块（不遮挡视线，魔杖绑定后跨维传送）
        PORTAL_BLOCK = Registry.register(Registries.BLOCK,
            new Identifier("bunkerconsole", "portal_block"),
            new PortalBlock(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque().luminance(state -> 10)));
        Registry.register(Registries.ITEM,
            new Identifier("bunkerconsole", "portal_block"),
            new BlockItem(PORTAL_BLOCK, new Item.Settings()));

        BunkerConfig.load();
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("immersive_portals")) {
                com.bunkerconsole.portal.PortalBridge.rebuildAllPortals(server);
            }
        });
        registerCommands();
    }

    private static void tick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        if (world == null) return;
        if (paused) return; // 暂停模式，什么都不做

        if (!resetting) {
            timer--;
            // 每20 tick（1秒）在actionbar显示剩余时间
            if (timer % 20 == 0 && timer > 0) {
                int sec = timer / 20;
                String msg;
                if (sec > 60) {
                    msg = "§e第" + day + "天 §f| §a剩余 " + (sec/60) + "分" + (sec%60) + "秒";
                } else if (sec > 10) {
                    msg = "§e第" + day + "天 §f| §e剩余 " + sec + "秒";
                } else {
                    msg = "§e第" + day + "天 §f| §c剩余 " + sec + "秒";
                }
                final String fmsg = msg;
                world.getPlayers().forEach(p -> p.sendMessage(Text.literal(fmsg), true));
            }
            if (timer <= 0) startReset(world);
        } else {
            resetPhase--;
            if (resetPhase <= 0) doReset(world);
        }

        // 展示框检测每10 tick（0.5秒）跑一次
        tickCounter++;
        if (tickCounter % 10 == 0) {
        correct = 0;
        List<BunkerConfig.FrameConfig> frames = BunkerConfig.frames;

        // 一次性搜索选区范围内所有物品展示框，而不是每个配置搜一次
        BlockPos min = BunkerConfig.getMinCorner();
        BlockPos max = BunkerConfig.getMaxCorner();
        Box area = new Box(min, max);
        List<ItemFrameEntity> allFrames = world.getEntitiesByType(
            net.minecraft.entity.EntityType.ITEM_FRAME, area, e -> true);

        // 匹配每个配置
        for (BunkerConfig.FrameConfig fc : frames) {
            for (ItemFrameEntity frame : allFrames) {
                BlockPos fp = frame.getBlockPos();
                if (fp.getX() == fc.pos.getX() && fp.getY() == fc.pos.getY() && fp.getZ() == fc.pos.getZ()) {
                    ItemStack stack = frame.getHeldItemStack();
                    if (stack != null && !stack.isEmpty()) {
                        String id = Registries.ITEM.getId(stack.getItem()).toString();
                        if (fc.item.equals(id)) {
                            correct++;
                            break;
                        }
                    }
                }
            }
        }

        if (correct == frames.size() && frames.size() > 0 && stage == 0) {
            stage = 1;
            doneTimer = 60; // 3秒后自动重置（20tick=1秒）
            world.setBlockState(BunkerConfig.redstoneSignal, Blocks.REDSTONE_BLOCK.getDefaultState());
            if (!doneTriggered) {
                doneTriggered = true;
                fireworkShow(world);
                // 判断是否快速完成（20秒内 = 400 tick）
                int elapsed = dayStartTick - timer;
                if (elapsed < 400) {
                    fastClearUnlocked = true;
                    broadcast(world, "§c§l警告：安全系统被绕过！所有配方已解锁！");
                } else {
                    broadcast(world, "§e§l全部任务物品已凑齐！3秒后重置...");
                }
            }
        } else if (correct < frames.size()) {
            world.setBlockState(BunkerConfig.redstoneSignal, Blocks.AIR.getDefaultState());
            doneTriggered = false;
            doneTimer = 0;
            if (stage == 1) stage = 0;
        }
        } // 关闭 if (tickCounter % 10 == 0)

        // stage=1时倒计时，3秒后自动reset
        if (stage == 1 && doneTimer > 0 && !resetting) {
            doneTimer--;
            if (doneTimer <= 0) {
                startReset(world);
            }
        }

        // 配方限制：非快速完成模式下，禁止合成危险物品
        if (!fastClearUnlocked) {
            world.getPlayers().forEach(p -> {
                if (p.currentScreenHandler instanceof net.minecraft.screen.CraftingScreenHandler handler) {
                    var result = handler.getSlot(0).getStack();
                    if (!result.isEmpty() && isBannedItem(result)) {
                        handler.getSlot(0).setStack(net.minecraft.item.ItemStack.EMPTY);
                    }
                }
            });
        }
    }

    // 黑名单物品：禁止合成
    private static boolean isBannedItem(ItemStack stack) {
        var item = stack.getItem();
        return item == net.minecraft.item.Items.TNT
            || item == net.minecraft.item.Items.FIRE_CHARGE
            || item == net.minecraft.item.Items.FIREWORK_ROCKET
            || item == net.minecraft.item.Items.FIREWORK_STAR
            || item == net.minecraft.item.Items.GUNPOWDER
            || item == net.minecraft.item.Items.TNT_MINECART;
    }

    private static boolean checkFrame(ServerWorld world, BunkerConfig.FrameConfig fc) {
        BlockPos pos = fc.pos;
        Box box = new Box(pos.getX()-1, pos.getY()-1, pos.getZ()-1,
                          pos.getX()+1, pos.getY()+1, pos.getZ()+1);
        List<ItemFrameEntity> frames = world.getEntitiesByType(EntityType.ITEM_FRAME, box, e -> true);
        for (ItemFrameEntity frame : frames) {
            ItemStack stack = frame.getHeldItemStack();
            if (stack != null && !stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).toString();
                if (fc.item.equals(id)) return true;
            }
        }
        return false;
    }

    private static void startReset(ServerWorld world) {
        resetting = true;
        resetPhase = 50;
        broadcast(world, "§7……一阵困意袭来……");
        world.getPlayers().forEach(p -> {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 1, true, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 250, true, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 1, true, false));
        });
    }

    private static void doReset(ServerWorld world) {
        // 保护：先检查模板区corner1位置有没有内容
        BlockPos tplCheck = new BlockPos(
            BunkerConfig.corner1.getX() + BunkerConfig.TEMPLATE_DX,
            BunkerConfig.corner1.getY(),
            BunkerConfig.corner1.getZ());
        var templateState = world.getBlockState(tplCheck);
        if (templateState.isAir()) {
            broadcast(world, "§c模板区是空的！先跑固化模板，再 reset！");
            return;
        }

        BlockPos min = BunkerConfig.getMinCorner();
        BlockPos max = BunkerConfig.getMaxCorner();

        world.getPlayers().forEach(p -> p.getInventory().clear());

        // 2. 用Java API清空选区
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                }
            }
        }

        // 3. 从模板区复制回来
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos src = new BlockPos(x + BunkerConfig.TEMPLATE_DX, y, z);
                    BlockPos dst = new BlockPos(x, y, z);
                    world.setBlockState(dst, world.getBlockState(src));
                }
            }
        }
        // 4. 恢复土豆箱内容（NBT方式，从固化时备份的数据恢复）
        if (potatoChestNbt != null) {
            var newBe = world.getBlockEntity(BunkerConfig.potatoChest);
            if (newBe != null) {
                newBe.readNbt(potatoChestNbt);
                newBe.markDirty();
            }
        }

        // 5. 清除选区里的掉落物
        Box area = new Box(min, max);
        world.getEntitiesByType(net.minecraft.entity.EntityType.ITEM, area, e -> true)
            .forEach(e -> e.discard());

        // 6. 发射器自动装填：第1格烟花火箭，第2格火药
        if (BunkerConfig.dispenserPos != null) {
            var dispBe = world.getBlockEntity(BunkerConfig.dispenserPos);
            if (dispBe instanceof net.minecraft.block.entity.DispenserBlockEntity disp) {
                disp.setStack(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.FIREWORK_ROCKET));
                disp.setStack(1, new net.minecraft.item.ItemStack(net.minecraft.item.Items.GUNPOWDER));
            }
        }

        // 7. 恢复告示牌内容（NBT方式）
        for (SignBackup sb : signBackups) {
            var signBlockEntity = world.getBlockEntity(sb.pos);
            if (signBlockEntity instanceof net.minecraft.block.entity.SignBlockEntity signBe) {
                signBe.readNbt(sb.nbt);
                signBe.markDirty();
            }
        }

        // 8. 恢复展示框实体（整个NBT恢复）
        for (FrameBackup fb : frameBackups) {
            ItemFrameEntity frame = EntityType.ITEM_FRAME.create(world);
            if (frame != null) {
                frame.readNbt(fb.nbt);
                world.spawnEntity(frame);
            }
        }

        world.getPlayers().forEach(p -> p.teleport(world, BunkerConfig.spawn.getX()+0.5, BunkerConfig.spawn.getY(), BunkerConfig.spawn.getZ()+0.5, 0, 0));
        day++;
        broadcast(world, "§e第 " + day + " 天 §f- 早上好，欢迎来到37号避难所");
        timer = BunkerConfig.dayTicks;
        dayStartTick = timer;
        fastClearUnlocked = false;
        resetting = false;
        correct = 0;
        stage = 0;
        doneTriggered = false;
        world.setBlockState(BunkerConfig.redstoneSignal, Blocks.AIR.getDefaultState());
    }

    public static void snapshot(ServerWorld world) {
        BlockPos min = BunkerConfig.getMinCorner();
        BlockPos max = BunkerConfig.getMaxCorner();

        // 清空旧的备份
        signBackups.clear();
        frameBackups.clear();

        // 备份土豆箱NBT
        potatoChestNbt = null;
        var potatoBe = world.getBlockEntity(BunkerConfig.potatoChest);
        if (potatoBe != null) {
            potatoChestNbt = potatoBe.createNbt();
        }

        // 收集选区里所有展示框实体（整个NBT备份）
        Box area = new Box(min, max);
        List<ItemFrameEntity> allFrames = world.getEntitiesByType(
            net.minecraft.entity.EntityType.ITEM_FRAME, area, e -> true);
        for (ItemFrameEntity frame : allFrames) {
            FrameBackup fb = new FrameBackup();
            fb.nbt = frame.writeNbt(new net.minecraft.nbt.NbtCompound());
            frameBackups.add(fb);
        }

        // 直接用Java API复制方块，同时收集告示牌内容
        int count = 0;
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos src = new BlockPos(x, y, z);
                    BlockPos dst = new BlockPos(x + BunkerConfig.TEMPLATE_DX, y, z);
                    var state = world.getBlockState(src);
                    world.setBlockState(dst, state);
                    count++;

                    // 收集告示牌内容（NBT方式）
                    var signBlockEntity = world.getBlockEntity(src);
                    if (signBlockEntity instanceof net.minecraft.block.entity.SignBlockEntity signBe) {
                        SignBackup sb = new SignBackup();
                        sb.pos = src.toImmutable();
                        sb.nbt = signBe.createNbt();
                        signBackups.add(sb);
                    }
                }
            }
        }

        // 验证
        BlockPos tplPos = new BlockPos(
            BunkerConfig.corner1.getX() + BunkerConfig.TEMPLATE_DX,
            BunkerConfig.corner1.getY(),
            BunkerConfig.corner1.getZ());
        var activityState = world.getBlockState(BunkerConfig.corner1);
        var tplState = world.getBlockState(tplPos);
        // 调试信息发聊天栏
        world.getPlayers().forEach(p -> {
            p.sendMessage(Text.literal("§f活动区 corner1 " + BunkerConfig.corner1.toShortString() + " → " +
                (activityState.isAir() ? "§7空气" : "§a" + activityState.getBlock().toString())), false);
            p.sendMessage(Text.literal("§f模板区 corner1 " + tplPos.toShortString() + " → " +
                (tplState.isAir() ? "§7空气" : "§a" + tplState.getBlock().toString())), false);
        });
        if (tplState.isAir()) {
            broadcast(world, "§c固化失败！模板区corner1还是空气");
        } else {
            broadcast(world, "§a模板固化成功！");
        }
    }

    private static void executeCommand(ServerWorld world, String cmd) {
        ServerCommandSource src = world.getServer().getCommandSource();
        world.getServer().getCommandManager().executeWithPrefix(src, cmd);
    }

    private static void broadcast(ServerWorld world, String msg) {
        world.getPlayers().forEach(p -> p.sendMessage(Text.literal(msg), false));
    }

    /** 按维度ID字符串获取 ServerWorld，找不到返回 null */
    public static ServerWorld getWorldByDim(MinecraftServer server, String dimId) {
        if (server == null || dimId == null) return null;
        Identifier id = new Identifier(dimId);
        return server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, id));
    }

    private static void fireworkShow(ServerWorld world) {
        for (int i = 0; i < 5; i++) {
            executeCommand(world, "summon minecraft:firework_rocket " + (i*4-8) + " 75 " + (i*4-8) +
                " {LifeTime:50,Item:{id:\"minecraft:firework_rocket\",Count:1b}}");
        }
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("bunker")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("reset").executes(ctx -> {
                    startReset(ctx.getSource().getWorld());
                    return 1;
                }))
                .then(CommandManager.literal("snapshot").executes(ctx -> {
                    snapshot(ctx.getSource().getWorld());
                    return 1;
                }))
                .then(CommandManager.literal("pause").executes(ctx -> {
                    paused = !paused;
                    ServerCommandSource src = ctx.getSource();
                    if (paused) {
                        src.sendFeedback(() -> Text.literal("§e模组已暂停！进入纯净建造模式，倒计时/检测/重置全部停止。"), false);
                    } else {
                        src.sendFeedback(() -> Text.literal("§a模组已恢复！继续正常运行。"), false);
                    }
                    return 1;
                }))
                .then(CommandManager.literal("tpltemplate").executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    var player = src.getPlayer();
                    if (player != null) {
                        BlockPos min = BunkerConfig.getMinCorner();
                        BlockPos max = BunkerConfig.getMaxCorner();
                        int tx = (min.getX() + max.getX()) / 2 + BunkerConfig.TEMPLATE_DX;
                        int ty = (min.getY() + max.getY()) / 2;
                        int tz = (min.getZ() + max.getZ()) / 2;
                        player.teleport(src.getWorld(), tx + 0.5, ty, tz + 0.5, 0, 0);
                        src.sendFeedback(() -> Text.literal("§a已传送到模板区中心: " + tx + "," + ty + "," + tz), false);
                    }
                    return 1;
                }))
                .then(CommandManager.literal("status").executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    src.sendFeedback(() -> Text.literal("§6=== 37号避难所 v" + VERSION + " ==="), false);
                    src.sendFeedback(() -> Text.literal("§f天数: §e" + day), false);
                    src.sendFeedback(() -> Text.literal("§f剩余: §e" + (timer/20) + "秒"), false);
                    src.sendFeedback(() -> Text.literal("§f进度: §e" + correct + "/" + BunkerConfig.frames.size()), false);
                    src.sendFeedback(() -> Text.literal("§f状态机: §e" + stageName()), false);
                    src.sendFeedback(() -> Text.literal("§f出生点: §e" + BunkerConfig.spawn.toShortString()), false);
                    BlockPos min = BunkerConfig.getMinCorner();
                    BlockPos max = BunkerConfig.getMaxCorner();
                    src.sendFeedback(() -> Text.literal("§f选区角1: §e" + BunkerConfig.corner1.toShortString()), false);
                    src.sendFeedback(() -> Text.literal("§f选区角2: §e" + BunkerConfig.corner2.toShortString()), false);
                    src.sendFeedback(() -> Text.literal("§f选区范围: §e" + min.getX() + "~" + max.getX() +
                        " y" + min.getY() + "~" + max.getY() +
                        " z" + min.getZ() + "~" + max.getZ()), false);
                    return 1;
                }))
                .then(CommandManager.literal("setspawn").executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    var player = src.getPlayer();
                    if (player != null) {
                        BunkerConfig.spawn = player.getBlockPos();
                        BunkerConfig.save();
                        src.sendFeedback(() -> Text.literal("§a出生点已设为: " + BunkerConfig.spawn.toShortString()), false);
                    }
                    return 1;
                }))
                .then(CommandManager.literal("corner1").executes(ctx -> {
                    var player = ctx.getSource().getPlayer();
                    if (player != null) {
                        BunkerConfig.corner1 = player.getBlockPos();
                        BunkerConfig.save();
                        ctx.getSource().sendFeedback(() -> Text.literal("§a角点1: " + BunkerConfig.corner1.toShortString()), false);
                    }
                    return 1;
                }))
                .then(CommandManager.literal("corner2").executes(ctx -> {
                    var player = ctx.getSource().getPlayer();
                    if (player != null) {
                        BunkerConfig.corner2 = player.getBlockPos();
                        BunkerConfig.save();
                        ctx.getSource().sendFeedback(() -> Text.literal("§a角点2: " + BunkerConfig.corner2.toShortString()), false);
                    }
                    return 1;
                }))
                .then(CommandManager.literal("debug").executes(ctx -> {
                    ServerWorld w = ctx.getSource().getWorld();
                    BlockPos min = BunkerConfig.getMinCorner();
                    BlockPos max = BunkerConfig.getMaxCorner();
                    BlockPos center = new BlockPos(
                        (min.getX()+max.getX())/2, (min.getY()+max.getY())/2, (min.getZ()+max.getZ())/2);
                    // 检查选区中心点和模板区对应点
                    BlockPos[] testPoints = {
                        center,                                              // 选区中心
                        new BlockPos(center.getX() + BunkerConfig.TEMPLATE_DX, center.getY(), center.getZ()), // 模板区对应点
                    };
                    ctx.getSource().sendFeedback(() -> Text.literal("§6=== 调试信息 ==="), false);
                    for (BlockPos p : testPoints) {
                        var state = w.getBlockState(p);
                        String blockName = state.getBlock().toString();
                        ctx.getSource().sendFeedback(() -> Text.literal(
                            "§f" + p.toShortString() + " → " + (state.isAir() ? "§7空气" : "§a" + blockName)), false);
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§f选区: " + min.toShortString() + " ~ " + max.toShortString()), false);
                    return 1;
                }))
                .then(CommandManager.literal("backupmode").executes(ctx -> {
                    BunkerConfig.backupMode = !BunkerConfig.backupMode;
                    BunkerConfig.selectingCorner1 = null;
                    if (BunkerConfig.backupMode) {
                        ctx.getSource().sendFeedback(() -> Text.literal("§e选区模式已开启：用魔杖右键第一个角点"), false);
                    } else {
                        ctx.getSource().sendFeedback(() -> Text.literal("§7选区模式已关闭"), false);
                    }
                    return 1;
                }))
                .then(CommandManager.literal("setTimer")
                    .then(CommandManager.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3600))
                    .executes(ctx -> {
                        int sec = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds");
                        BunkerConfig.dayTicks = sec * 20;
                        BunkerConfig.save();
                        timer = BunkerConfig.dayTicks;
                        ctx.getSource().sendFeedback(() -> Text.literal("a一天时长设为 " + sec + " 秒"), false);
                        return 1;
                    })))
                .then(CommandManager.literal("setframe")
                    .then(CommandManager.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                    .then(CommandManager.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                    .then(CommandManager.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                    .then(CommandManager.argument("item", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x");
                            int y = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y");
                            int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z");
                            String item = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "item");
                            BunkerConfig.FrameConfig fc = BunkerConfig.getOrCreate(new net.minecraft.util.math.BlockPos(x, y, z));
                            fc.item = item;
                            BunkerConfig.save();
                            ctx.getSource().sendFeedback(() -> Text.literal("§a已配置展示框 " + x + "," + y + "," + z + " → " + item), false);
                            return 1;
                        })))))
                .then(CommandManager.literal("levels").executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    src.sendFeedback(() -> Text.literal("§6=== 可用关卡维度 ==="), false);
                    for (String d : LEVEL_DIMS) {
                        src.sendFeedback(() -> Text.literal("§f- §e" + d), false);
                    }
                    src.sendFeedback(() -> Text.literal("§6=== 传送门绑定 (" + BunkerConfig.portalLinks.size() + ") ==="), false);
                    for (BunkerConfig.PortalLink l : BunkerConfig.portalLinks) {
                        src.sendFeedback(() -> Text.literal("§f" + l.dimA + " " + l.ax + "," + l.ay + "," + l.az +
                            " §7↔ §f" + l.dimB + " " + l.bx + "," + l.by + "," + l.bz), false);
                    }
                    return 1;
                }))
                .then(CommandManager.literal("createlevel")
                    .then(CommandManager.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                        String dimId = raw.contains(":") ? raw : "bunkerconsole:" + raw;
                        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("immersive_portals")) {
                            src.sendFeedback(() -> Text.literal("§c未安装 Immersive Portals，无法动态创建关卡维度。"), false);
                            return 0;
                        }
                        com.bunkerconsole.portal.PortalBridge.createLevelDimension(src.getServer(), dimId);
                        src.sendFeedback(() -> Text.literal("§a已创建关卡维度: §e" + dimId), false);
                        return 1;
                    })))
            );
        });
    }

    private static String stageName() {
        return switch (stage) { case 1 -> "任务完成"; case 2 -> "过载逃脱"; default -> "生态圈探索"; };
    }
}
