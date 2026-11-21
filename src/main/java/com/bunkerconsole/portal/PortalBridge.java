package com.bunkerconsole.portal;

import com.bunkerconsole.BunkerConfig;
import com.bunkerconsole.BunkerMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.api.DimensionAPI;

/**
 * Immersive Portals 对接层：把配置里的传送门绑定变成 IP 透视门实体，
 * 由 IP 负责隔门渲染对面视角与走进无缝跨维传送。
 * <p>运行期需要玩家安装 Immersive Portals（客户端+服务端）。
 */
public final class PortalBridge {

    /** 标记本 mod 生成的 IP 门实体，便于重建时清理旧门 */
    public static final String PORTAL_TAG = "bunkerconsole_portal";

    private PortalBridge() {}

    /** 为所有已绑定链接重建透视门（先清理各世界里本 mod 的旧门实体） */
    public static void rebuildAllPortals(MinecraftServer server) {
        for (BunkerConfig.PortalLink link : BunkerConfig.portalLinks) {
            spawnLinkPortals(server, link);
        }
    }

    private static void spawnLinkPortals(MinecraftServer server, BunkerConfig.PortalLink link) {
        // A 端门 -> 指向 B
        ServerWorld worldA = BunkerMod.getWorldByDim(server, link.dimA);
        if (worldA != null) {
            clearOldPortals(worldA);
            Portal aToB = makePortal(worldA,
                link.dimA, link.ax, link.ay, link.az,
                link.dimB, link.bx, link.by, link.bz);
            PortalAPI.spawnServerEntity(aToB);
        }
        // B 端门 -> 指向 A
        ServerWorld worldB = BunkerMod.getWorldByDim(server, link.dimB);
        if (worldB != null) {
            clearOldPortals(worldB);
            Portal bToA = makePortal(worldB,
                link.dimB, link.bx, link.by, link.bz,
                link.dimA, link.ax, link.ay, link.az);
            PortalAPI.spawnServerEntity(bToA);
        }
    }

    private static Portal makePortal(ServerWorld origin,
                                     String fromDim, int fx, int fy, int fz,
                                     String toDim, int tx, int ty, int tz) {
        Portal p = new Portal(Portal.entityType, origin);
        p.setOriginPos(new Vec3d(fx + 0.5, fy, fz + 0.5));
        p.setDestinationDimension(RegistryKey.of(RegistryKeys.WORLD, new Identifier(toDim)));
        p.setDestination(new Vec3d(tx + 0.5, ty + 1.0, tz + 0.5));
        p.axisW = new Vec3d(1.0, 0.0, 0.0);
        p.axisH = new Vec3d(0.0, 1.0, 0.0);
        p.width = 1.0;
        p.height = 2.0;
        p.teleportable = true;
        p.portalTag = PORTAL_TAG;
        return p;
    }

    private static void clearOldPortals(ServerWorld world) {
        for (net.minecraft.entity.Entity e : world.iterateEntities()) {
            if (e instanceof Portal p && PORTAL_TAG.equals(p.portalTag)) {
                p.discard();
            }
        }
    }

    /** 动态新增一个关卡维度，复用主世界的维度类型与地形生成器 */
    public static void createLevelDimension(MinecraftServer server, String dimId) {
        ServerWorld over = server.getOverworld();
        if (over == null) return;
        ChunkGenerator gen = over.getChunkManager().getChunkGenerator();
        DimensionOptions opts = new DimensionOptions(over.getDimensionEntry(), gen);
        DimensionAPI.addDimensionDynamically(new Identifier(dimId), opts);
    }
}
