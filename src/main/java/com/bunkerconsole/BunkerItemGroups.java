package com.bunkerconsole;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class BunkerItemGroups {
    public static final ItemGroup GROUP = FabricItemGroup.builder()
        .icon(() -> new ItemStack(BunkerMod.CONFIGURATOR))
        .displayName(Text.literal("避难所控制台"))
        .build();
}
