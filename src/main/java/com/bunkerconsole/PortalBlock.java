package com.bunkerconsole;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;

/**
 * 传送门方块：半透明、不遮挡视线，可直接看到门后场景。
 * 双向配对由魔杖绑定，右键已绑定的门跨维度传送。
 */
public class PortalBlock extends Block {
    public PortalBlock(Settings settings) {
        super(settings);
    }
}
