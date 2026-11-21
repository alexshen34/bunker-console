package com.bunkerconsole.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class BunkerScreen extends Screen {
    private final Screen parent;

    public BunkerScreen(net.minecraft.client.MinecraftClient client, Screen parent) {
        super(Text.literal("37号避难所控制台 v" + com.bunkerconsole.BunkerMod.VERSION));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 4 + 20;

        addDrawableChild(ButtonWidget.builder(Text.literal("手动重置循环"), b -> {
            sendCmd("bunker reset");
        }).dimensions(cx - 100, y, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("固化模板（备份当前场景）"), b -> {
            sendCmd("bunker snapshot");
        }).dimensions(cx - 100, y + 25, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("查看状态"), b -> {
            sendCmd("bunker status");
        }).dimensions(cx - 100, y + 50, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("设为当前位置（出生点）"), b -> {
            sendCmd("bunker setspawn");
        }).dimensions(cx - 100, y + 75, 200, 20).build());

        // 选区模式按钮（发命令让服务端切换状态）
        addDrawableChild(ButtonWidget.builder(Text.literal("§a进入选区模式（魔杖右键两个角）"), b -> {
            sendCmd("bunker backupmode");
        }).dimensions(cx - 100, y + 100, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("时间设为5分钟"), b -> {
            sendCmd("bunker setTimer 300");
        }).dimensions(cx - 100, y + 130, 95, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("时间设为30秒"), b -> {
            sendCmd("bunker setTimer 30");
        }).dimensions(cx + 5, y + 130, 95, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("§6暂停/恢复模组（纯净建造模式）"), b -> {
            sendCmd("bunker pause");
        }).dimensions(cx - 100, y + 155, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close()).dimensions(cx - 100, y + 180, 200, 20).build());
    }

    private void sendCmd(String cmd) {
        if (this.client != null && this.client.player != null) {
            this.client.player.networkHandler.sendChatCommand(cmd);
        }
        close();
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 40, 0xFFFF55);
        context.drawCenteredTextWithShadow(this.textRenderer,
            "按K随时打开此面板 · 状态请点「查看状态」",
            this.width / 2, 60, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
