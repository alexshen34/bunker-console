package com.bunkerconsole.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public class BunkerConsoleClient implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.bunkerconsole.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.bunkerconsole"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null) client.setScreen(new BunkerScreen(client, null));
            }
        });

        // 魔杖右键展示框 → 打开物品ID输入GUI
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient && entity instanceof ItemFrameEntity frame) {
                ItemStack stack = player.getStackInHand(hand);
                if (stack.getItem() == com.bunkerconsole.BunkerMod.CONFIGURATOR) {
                    BlockPos pos = frame.getBlockPos();
                    MinecraftClient.getInstance().setScreen(new FrameConfigScreen(pos));
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });

        // 服务端：魔杖右键方块
        // 服务端：魔杖右键方块（只在服务端处理，避免单人游戏重复触发）
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() != com.bunkerconsole.BunkerMod.CONFIGURATOR) return ActionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();

            // 备份模式：记录角点
            if (com.bunkerconsole.BunkerConfig.backupMode) {
                if (com.bunkerconsole.BunkerConfig.selectingCorner1 == null) {
                    com.bunkerconsole.BunkerConfig.selectingCorner1 = pos;
                    player.sendMessage(Text.literal("§a角点1已设置: " + pos.toShortString()), false);
                    player.sendMessage(Text.literal("§e再右键第二个对角点"), false);
                } else {
                    com.bunkerconsole.BunkerConfig.corner1 = com.bunkerconsole.BunkerConfig.selectingCorner1;
                    com.bunkerconsole.BunkerConfig.corner2 = pos;
                    com.bunkerconsole.BunkerConfig.save();
                    com.bunkerconsole.BunkerConfig.backupMode = false;
                    com.bunkerconsole.BunkerConfig.selectingCorner1 = null;
                    // 计算选区大小
                    int dx = Math.abs(com.bunkerconsole.BunkerConfig.corner2.getX() - com.bunkerconsole.BunkerConfig.corner1.getX()) + 1;
                    int dy = Math.abs(com.bunkerconsole.BunkerConfig.corner2.getY() - com.bunkerconsole.BunkerConfig.corner1.getY()) + 1;
                    int dz = Math.abs(com.bunkerconsole.BunkerConfig.corner2.getZ() - com.bunkerconsole.BunkerConfig.corner1.getZ()) + 1;
                    player.sendMessage(Text.literal("§a选区已设置: " +
                        com.bunkerconsole.BunkerConfig.corner1.toShortString() + " ~ " +
                        com.bunkerconsole.BunkerConfig.corner2.toShortString()), false);
                    player.sendMessage(Text.literal("§f大小: §e" + dx + "×" + dy + "×" + dz +
                        " = " + (dx*dy*dz) + " 方块"), false);
                    player.sendMessage(Text.literal("§e按K打开控制台，点「固化模板」备份此选区"), false);
                }
                return ActionResult.SUCCESS;
            }

            // 普通模式：右键箱子设土豆箱，右键发射器设为自动装填发射器，其他设红石信号位置
            var state = world.getBlockState(pos);

            // 魔杖右键传送门方块 → 双向绑定
            if (state.getBlock() == com.bunkerconsole.BunkerMod.PORTAL_BLOCK) {
                String dimId = world.getRegistryKey().getValue().toString();
                if (com.bunkerconsole.BunkerConfig.pendingPortal == null) {
                    com.bunkerconsole.BunkerConfig.pendingPortal = new com.bunkerconsole.BunkerConfig.PortalLink(
                        dimId, pos.getX(), pos.getY(), pos.getZ(), null, 0, 0, 0);
                    player.sendMessage(Text.literal("§a传送门 A 已记录: " + dimId + " " + pos.toShortString()), false);
                    player.sendMessage(Text.literal("§e再右键目标维度的传送门完成绑定"), false);
                } else {
                    com.bunkerconsole.BunkerConfig.PortalLink p = com.bunkerconsole.BunkerConfig.pendingPortal;
                    com.bunkerconsole.BunkerConfig.PortalLink link = new com.bunkerconsole.BunkerConfig.PortalLink(
                        p.dimA, p.ax, p.ay, p.az, dimId, pos.getX(), pos.getY(), pos.getZ());
                    com.bunkerconsole.BunkerConfig.portalLinks.add(link);
                    com.bunkerconsole.BunkerConfig.save();
                    com.bunkerconsole.BunkerConfig.pendingPortal = null;
                    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("immersive_portals")) {
                        com.bunkerconsole.portal.PortalBridge.rebuildAllPortals(world.getServer());
                    }
                    player.sendMessage(Text.literal("§a传送门已双向绑定: " +
                        p.dimA + " " + p.ax + "," + p.ay + "," + p.az + " ↔ " +
                        dimId + " " + pos.toShortString()), false);
                }
                return ActionResult.SUCCESS;
            }

            if (state.getBlock() instanceof ChestBlock) {
                com.bunkerconsole.BunkerConfig.potatoChest = pos;
                com.bunkerconsole.BunkerConfig.save();
                player.sendMessage(Text.literal("§a土豆箱已设置: " + pos.toShortString()), false);
            } else if (state.getBlock() instanceof net.minecraft.block.DispenserBlock) {
                com.bunkerconsole.BunkerConfig.dispenserPos = pos;
                com.bunkerconsole.BunkerConfig.save();
                player.sendMessage(Text.literal("§a自动装填发射器已设置: " + pos.toShortString()), false);
            } else {
                com.bunkerconsole.BunkerConfig.redstoneSignal = pos;
                com.bunkerconsole.BunkerConfig.save();
                player.sendMessage(Text.literal("§a红石信号位置已设置: " + pos.toShortString()), false);
            }
            return ActionResult.SUCCESS;
        });
    }
}
