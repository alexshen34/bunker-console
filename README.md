# 37号避难所 · Bunker Console

Minecraft **1.20.1 / Fabric** 循环解谜地图控制台 Mod。围绕「每日重置的避难所关卡」提供模板备份恢复、物品检测、红石触发、跨轮回储物、透视传送门与动态关卡维度等能力。

- Mod 版本：**2.5.0**
- 游戏版本：**Minecraft 1.20.1**
- 加载器：**Fabric Loader 0.19.5+**
- 语言：Java（Gradle + Fabric Loom）

## 功能

- **每日重置循环**：倒计时结束后清空活动选区，从模板区复制回场景，传送回出生点，天数 +1。
- **展示框物品检测**：魔杖右键展示框绑定物品 ID，凑齐后触发红石信号。
- **红石信号位置**：所有绑定物品凑齐后，在指定位置自动生成红石块。
- **土豆箱跨轮回储物**：指定箱子内容物在每次重置后自动备份并恢复。
- **发射器自动装填**：重置后自动往指定发射器填入烟花火箭 + 火药，并清理选区掉落物。
- **告示牌内容恢复**：固化模板时备份告示牌文字，重置后自动写回。
- **头顶倒计时**：actionbar 实时显示剩余时间，最后 10 秒红色警告。
- **魔杖选区**：K 键进入选区模式，魔杖右键两个对角确定备份范围。
- **GUI 控制台**：按 `K` 打开面板，一键重置 / 固化模板 / 选区 / 设出生点。
- **IP 透视传送门**：接入 [Immersive Portals](https://modrinth.com/mod/immersive-portals)，隔门实时透视对面维度视角，走进门无缝跨维（无加载屏）。
- **动态关卡维度**：游戏内命令新建关卡维度，复用主世界地形与生物群系预设。

## 运行依赖

| 依赖 | 版本 / 说明 |
| --- | --- |
| Minecraft | 1.20.1 |
| Fabric Loader | 0.19.5+ |
| Fabric API | 0.92.12+1.20.1 |
| Immersive Portals | 5.2.0-mc1.20.1（客户端 + 服务端均需安装，强制前置） |

## 构建

### 环境要求

- JDK **21**
- Gradle 由 wrapper 提供（9.7.1），无需单独安装

### 编译命令

```bash
./gradlew build
```

产物位于 `build/libs/`：

- `bunkerconsole-<version>.jar`：可直接放入 `mods/` 的主 Mod
- `bunkerconsole-<version>-sources.jar`：源码包

### 编译期 API 依赖（libs/）

本 Mod 编译期需要 Immersive Portals 拆出的两个嵌套库 jar，放入 `libs/` 目录：

- `libs/imm_ptl_core-5.2.0.jar`（透视门实体与 API）
- `libs/q_misc_util-5.2.0.jar`（动态维度 API）

获取方式：下载 IP 5.2.0-mc1.20.1 外层 jar 后，从其 JiJ 嵌套内容中拆出上述两个 jar。运行时仍需玩家自行安装完整 IP Mod，本 Mod 不打包 IP。

## 游戏内命令

| 命令 | 作用 |
| --- | --- |
| `/bunker reset` | 手动触发一次重置 |
| `/bunker status` | 查看当前状态 |
| `/bunker debug` | 调试选区与模板区方块 |
| `/bunker setTimer <ticks>` | 修改一天时长（刻，20 刻 = 1 秒） |
| `/bunker levels` | 查看关卡维度与传送门绑定 |
| `/bunker createlevel <id>` | 动态新建关卡维度（ID 不带命名空间时默认 `bunkerconsole:`） |

## 物品

| 物品 | 获取 | 用途 |
| --- | --- | --- |
| 配置魔杖 `bunkerconsole:configurator` | `/give @s bunkerconsole:configurator` | 绑定展示框 / 土豆箱 / 发射器 / 红石位置 / 传送门 |
| 光源方块 `bunkerconsole:glow_block` | `/give @s bunkerconsole:glow_block` | 高亮度照明 |
| 传送门方块 `bunkerconsole:portal_block` | `/give @s bunkerconsole:portal_block` | 放置传送门，魔杖双向绑定 |

## 目录结构

```
src/main/java/com/bunkerconsole/
├── BunkerMod.java            # 入口、命令、重置循环、检测调度
├── BunkerConfig.java         # 配置持久化（选区/展示框/传送门绑定等）
├── GlowBlock.java            # 光源方块
├── PortalBlock.java          # 传送门方块
├── client/                   # 客户端：GUI 面板、魔杖交互
└── portal/PortalBridge.java   # Immersive Portals 对接层
src/main/resources/
├── fabric.mod.json
├── assets/bunkerconsole/     # 方块状态、模型、纹理、物品图标
└── data/bunkerconsole/worldgen/dimension/  # 预留关卡维度
```

## 协议

本项目以 **CC0 1.0 Universal** 发布，详见 [LICENSE](LICENSE)。可自由使用、修改与再分发。

> 第三方依赖（Fabric、Fabric API、Immersive Portals 等）遵循其各自许可协议。
