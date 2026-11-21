package com.bunkerconsole;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 配置持久化 */
public class BunkerConfig {

    public static class FrameConfig {
        public int x, y, z;
        public String item;
        public transient BlockPos pos;
        public FrameConfig() {}
        public FrameConfig(BlockPos p, String item) { this.x=p.getX(); this.y=p.getY(); this.z=p.getZ(); this.item=item; this.pos=p; }
    }

    public static List<FrameConfig> frames = new ArrayList<>();
    public static BlockPos spawn = new BlockPos(0, 66, 0);
    public static BlockPos redstoneSignal = new BlockPos(14, 66, 10);
    public static BlockPos potatoChest = new BlockPos(0, 65, 5);
    public static BlockPos dispenserPos = null; // 发射器位置（自动填烟花+火药）
    public static int dayTicks = 6000;

    /** 传送门双向绑定：A 门 ↔ B 门（各记录维度ID + 坐标） */
    public static class PortalLink {
        public String dimA; public int ax, ay, az;
        public String dimB; public int bx, by, bz;
        public PortalLink() {}
        public PortalLink(String dimA, int ax, int ay, int az, String dimB, int bx, int by, int bz) {
            this.dimA=dimA; this.ax=ax; this.ay=ay; this.az=az;
            this.dimB=dimB; this.bx=bx; this.by=by; this.bz=bz;
        }
    }
    public static List<PortalLink> portalLinks = new ArrayList<>();

    /** 魔杖绑定时暂存的第一扇门（只填 A 端），null 表示未在等待配对 */
    public static PortalLink pendingPortal = null;

    // 选区：两个对角点
    public static BlockPos corner1 = new BlockPos(-48, 34, -48);
    public static BlockPos corner2 = new BlockPos(48, 100, 48);
    public static int TEMPLATE_DX = 100000;

    // 备份模式状态（客户端用）
    public static boolean backupMode = false;
    public static BlockPos selectingCorner1 = null; // 正在选的角点1

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("bunkerconsole.json");
    }

    public static void load() {
        Path p = getConfigPath();
        if (p.toFile().exists()) {
            try (Reader r = new FileReader(p.toFile())) {
                Data cfg = gson.fromJson(r, Data.class);
                if (cfg != null) {
                    frames = cfg.frames != null ? cfg.frames : new ArrayList<>();
                    spawn = cfg.spawn != null ? cfg.spawn : new BlockPos(0,66,0);
                    redstoneSignal = cfg.redstoneSignal != null ? cfg.redstoneSignal : new BlockPos(14,66,10);
                    potatoChest = cfg.potatoChest != null ? cfg.potatoChest : new BlockPos(0,65,5);
                    dispenserPos = cfg.dispenserPos;
                    dayTicks = cfg.dayTicks;
                    corner1 = cfg.corner1 != null ? cfg.corner1 : new BlockPos(-48, 34, -48);
                    corner2 = cfg.corner2 != null ? cfg.corner2 : new BlockPos(48, 100, 48);
                    portalLinks = cfg.portalLinks != null ? cfg.portalLinks : new ArrayList<>();
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        for (FrameConfig fc : frames) fc.pos = new BlockPos(fc.x, fc.y, fc.z);

        if (frames.isEmpty()) {
            int[] xs = {-8, -4, 0, 4, 8};
            String[] items = {"minecraft:wheat","minecraft:coal","minecraft:iron_ingot","minecraft:gold_ingot","minecraft:diamond"};
            for (int i = 0; i < 5; i++) frames.add(new FrameConfig(new BlockPos(xs[i], 67, 7), items[i]));
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(getConfigPath().toFile())) {
            Data d = new Data();
            d.frames = frames;
            d.spawn = spawn;
            d.redstoneSignal = redstoneSignal;
            d.potatoChest = potatoChest;
            d.dispenserPos = dispenserPos;
            d.dayTicks = dayTicks;
            d.corner1 = corner1;
            d.corner2 = corner2;
            d.portalLinks = portalLinks;
            gson.toJson(d, w);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static class Data {
        public List<FrameConfig> frames;
        public BlockPos spawn;
        public BlockPos redstoneSignal;
        public BlockPos potatoChest;
        public BlockPos dispenserPos;
        public int dayTicks;
        public BlockPos corner1;
        public BlockPos corner2;
        public List<PortalLink> portalLinks;
    }

    public static FrameConfig getOrCreate(BlockPos pos) {
        for (FrameConfig fc : frames) {
            if (fc.x == pos.getX() && fc.y == pos.getY() && fc.z == pos.getZ()) return fc;
        }
        FrameConfig fc = new FrameConfig(pos, "");
        frames.add(fc);
        return fc;
    }

    /** 获取选区最小角 */
    public static BlockPos getMinCorner() {
        return new BlockPos(
            Math.min(corner1.getX(), corner2.getX()),
            Math.min(corner1.getY(), corner2.getY()),
            Math.min(corner1.getZ(), corner2.getZ())
        );
    }

    /** 获取选区最大角 */
    public static BlockPos getMaxCorner() {
        return new BlockPos(
            Math.max(corner1.getX(), corner2.getX()),
            Math.max(corner1.getY(), corner2.getY()),
            Math.max(corner1.getZ(), corner2.getZ())
        );
    }

    /** 查找包含给定门的绑定；无匹配返回 null */
    public static PortalLink findLink(String dim, BlockPos pos) {
        for (PortalLink l : portalLinks) {
            if (l.dimA.equals(dim) && l.ax==pos.getX() && l.ay==pos.getY() && l.az==pos.getZ()) return l;
            if (l.dimB.equals(dim) && l.bx==pos.getX() && l.by==pos.getY() && l.bz==pos.getZ()) return l;
        }
        return null;
    }

    /** 判断给定门在绑定里是否为 A 端 */
    public static boolean isSideA(PortalLink l, String dim, BlockPos pos) {
        return l.dimA.equals(dim) && l.ax==pos.getX() && l.ay==pos.getY() && l.az==pos.getZ();
    }
}
