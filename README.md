<h1 align="center">GT-Interesting-Thing</h1>
<p align="center"><strong><em>GTNH Interesting Gadgets Mod</em></strong><br><strong><em>GTNH 趣味道具模组</em></strong></p>

A GregTech New Horizons gadget mod that **provides interesting items enhancing the gameplay experience**, including flight cores and ore scanning tools, while balancing usage costs to maintain progression integrity.

一个 GregTech New Horizons 趣味道具模组，**提供增强游玩体验的有趣物品**，包括浮空核心和探矿工具，同时平衡使用代价以保持进阶完整性。

> [!NOTE]
> This is an unofficial mod. Please avoid discussing this mod in official GTNH forums.
> 这是一个非官方模组，讨论此模组时请注意场合。

## Downloads & Requirements / 下载与版本需求

| GTNH | GTIT | Maintenance / 维护 |
|------|------|:---:|
| 2.9.0 beta-1 | 1.0.0+ | ✔️ |
| 2.8.4 | 0.1.x | ✔️ |

---

## Items / 物品

### Float Core / 浮空核心

<p align="center"><img src="images/float_core.png" width="128"><br><em>浮空核心 / Float Core</em></p>

A simple yet powerful flight enabler. Equip to any Baubles slot to gain creative-like flight ability.

简单而强大的飞行道具。装备到任意 Baubles 饰品栏即可获得类似创造模式的飞行能力。

- Equip to any Baubles slot to gain flight
- Consumes hunger per tick while flying
- Automatically disables flight when hunger drops below 3 shanks (6 hunger points)
- Early-game accessible — no electricity required

- 装备到任意 Baubles 饰品栏即获得飞行能力
- 飞行时每 tick 消耗饥饿值
- 饥饿值低于3格（6点）时自动禁用飞行
- 前期即可获取——无需电力

---

### Electric Float Core / 电力浮空核心

<p align="center"><img src="images/electric_float_core.png" width="128"><br><em>电力浮空核心 / Electric Float Core</em></p>

An upgraded version of the Float Core with massive EU storage. When electricity runs out, it falls back to hunger consumption at half the rate.

浮空核心的升级版，拥有超大 EU 容量。电力耗尽时回退至饥饿消耗，消耗量为浮空核心的一半。

- LV voltage tier, 32,000,000 EU capacity
- Consumes electricity while flying
- Falls back to hunger consumption when depleted (half rate of Float Core)
- Compatible with IC2 charging stations

- LV 电压等级，32,000,000 EU 容量
- 飞行时消耗电力
- 电力耗尽时消耗饥饿值（浮空核心的一半消耗率）
- 兼容 IC2 充电站

---

### Telekinesis Ore Scanner Core / 念力共振探矿核心

<p align="center"><img src="images/telekinesis_ore_scanner_core.png" width="128"><br><em>念力共振探矿核心 / Telekinesis Ore Scanner Core</em></p>

A long-range ore and fluid prospecting tool that integrates with JourneyMap via VisualProspecting. Scan results are uploaded to the map automatically — you cannot view ore info directly, preserving the exploration challenge.

远程矿石和流体勘探工具，通过 VisualProspecting 与旅行地图集成。扫描结果自动上传至地图——无法直接获取矿石信息，保留探索挑战性。

- Right-click air or block to scan a 19×19 chunk area
- Consumes 6 hunger points per scan
- Data automatically uploaded to JourneyMap (requires VisualProspecting)
- Cannot view ore info directly — only map markers
- Shift + Right-click to switch between Ore / Fluid detection mode
- Requires VisualProspecting mod for map integration

- 右击空气或方块进行 19×19 区块大范围探矿
- 每次消耗 6 点饥饿值进行探矿
- 数据自动上传至旅行地图（需要 VisualProspecting）
- 无法直接获取矿石信息——仅显示地图标记
- Shift + 右键切换矿石/流体探测模式
- 需要 VisualProspecting 模组实现地图联动

---

## Multiblock Test Machine / 多方块测试机器

A test multiblock machine (HV tier) for verifying the multiblock registration process, structure detection logic, and recipe system integration. Uses a 3×3×3 hollow TungstenSteel structure.

测试用多方块机器（HV 级），用于验证多方块机器的注册流程、结构检测逻辑及配方系统。采用 3×3×3 空心钨钢结构。

---

## Tech Stack / 技术栈

- Java 8 (JVM Downgrader) / Minecraft 1.7.10 / Forge 10.13.4.1614
- ModularUI / ModularUI2 / StructureLib
- Dependencies: GT5-Unofficial (5.09.52.594), GTNHLib, VisualProspecting, Baubles, IC2

## License / 许可证

See LICENSE file.
详见 LICENSE 文件。
