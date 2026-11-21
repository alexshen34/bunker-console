package com.bunkerconsole.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** 右键展示框后弹出的配置GUI：输入物品ID */
public class FrameConfigScreen extends Screen {
    private final BlockPos framePos;
    private TextFieldWidget itemInput;

    public FrameConfigScreen(BlockPos pos) {
        super(Text.literal("配置展示框"));
        this.framePos = pos;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height / 4 + 20;

        itemInput = new TextFieldWidget(this.textRenderer, cx - 100, y, 200, 20,
            Text.literal("物品ID，如 minecraft:wheat"));
        itemInput.setMaxLength(64);
        itemInput.setText("minecraft:");
        addSelectableChild(itemInput);
        setInitialFocus(itemInput);

        addDrawableChild(ButtonWidget.builder(Text.literal("保存配置"), b -> {
            String item = itemInput.getText().trim();
            if (!item.isEmpty() && this.client != null && this.client.player != null) {
                String cmd = String.format("bunker setframe %d %d %d %s",
                    framePos.getX(), framePos.getY(), framePos.getZ(), item);
                this.client.player.networkHandler.sendChatCommand(cmd);
            }
            close();
        }).dimensions(cx - 100, y + 30, 95, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), b -> close())
            .dimensions(cx + 5, y + 30, 95, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 40, 0x55FF55);
        context.drawCenteredTextWithShadow(this.textRenderer,
            "展示框位置: " + framePos.toShortString(), this.width / 2, 60, 0xAAAAAA);
        itemInput.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
