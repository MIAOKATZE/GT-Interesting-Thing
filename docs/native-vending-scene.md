# Native vending output scene

The V2 GUI calls `VendingSceneBridge.attach(gui, machine, syncManager, ioColumnPanel)` after the normal dual-side tree is assembled. Its explicitly named sync handler exists on both sides. The client-only descendants own no `SyncHandler`; their ordinary `StringValue` is local. The 100 actual `ItemSlot` references registered by `IoColumnPanel` remain the sole stack source. There is no parallel inventory or client stock decrement.

## Boundary and wire contract

* `getOutputSceneFrame(afterRevision)` is sampled by the server container at most every four ticks (5 Hz), sent only when revision changes. Opening asks for a full snapshot.
* Schema 1 / `neko-room-v1`, maximum 100 bodies, 100 removals, and 65,536 uncompressed NBT bytes per packet. NBT allocations additionally use `NBTSizeTracker`. Finite bounds, normalized rotations, positive identities/quantity, slot range, duplicate identities and post-delta exclusive slot ownership are validated before commit.
* The receive queue is bounded to 16 frames. Overflow discards the incomplete delta chain and requests a fresh full frame. No partial ownership update is committed.
* Interpolation is display-only, 200 ms, shortest-path normalized quaternion interpolation with room-bound clamping. No `SceneSimulation`, entity, world tick hook, player camera lease, world mutation, or physical state is created by the GUI.

## Interactions

The statistics rail groups exact item+metadata+NBT across independent drop IDs. Hover/selection highlights every matching body. Its quantities come from the live synchronized slots, not the scene's informational quantity. All/half/custom buttons send only selected drop ID, generation, mode and bounded amount. The server's existing guarded GUI-session scheduler revalidates the current container; `takeOutputSceneGroup` re-derives matching stock and accepts only what the player inventory can actually receive. Shift-left-click is the same guarded group-all request. Right drag rotates only the display camera. Picking inverts the exact orthographic camera and intersects each visible body’s quaternion-oriented box; the nearest nonnegative ray hit wins regardless of insertion order. The same voxel boxes drawn for room walls, floor, outlets and shutter slats supply opaque occlusion, so hidden stock cannot be shift-taken through room solids. This is bounded model-box picking, not exact per-triangle mesh picking.

Pause freezes the displayed pose snapshot, not frames, selection, inventory transactions, ME processing or physics authority. Reduced motion disables interpolation, cat choreography and decorative particles. Door positions remain authoritative; pause also freezes their displayed pose. Neither setting changes the server simulation policy.

## Presentation and resource lifetime

The warm room uses narrow timber planks and full-height side walls (the latest user requirement supersedes the historical cutaway), a woven cat mat, compact rear planters/lanterns, a slatted retracting rear shutter and two five-panel side outlet shells with real inward-swinging paw doors. Door openness is driven by authoritative `doorLeft`/`doorRight`/`doorRear` values and interpolated over the same 200 ms presentation timeline as items, never inferred from client item counts. The doors use directional scene shading; shell/floor geometry does not intersect thin item proxies. Per-drop `route`, `deliveryStage` (WAIT_DOOR/FEED/RELEASED) and `deliveryAgeTicks` are bounded and decoded atomically. WAIT_DOOR owns the same real slots but contributes no rendered/pickable proxy; FEED first becomes visible after the server has opened the corresponding door. Native Minecraft block/item renderers are supplied by Core. The black cat uses Minecraft's `ModelOcelot` and `minecraft:textures/entity/cat/black.png`; geometry and texture are not copied into the mod. Native `ModelBox` geometry is drawn directly, avoiding `ModelRenderer` retained display-list allocations. The cat follows a finite event-triggered watch → clear-corridor approach to a settled low item → sniff/paw → return → sit sequence (at most 9.4 seconds, or watch only when the route is blocked). Initial full snapshots do not replay old interactions, repeated triggers cannot extend an active sequence, and idle/reduced-motion states perform no choreography updates. Native standing/sitting pivots are blended directly without creating an Entity. The opaque cat is rendered with the room before Core’s depth-aware translucent item pass; each native ModelBox also supplies a matching transformed OBB for mouse occlusion. Particles and outlines remain after the item pass.

ME phase 1 remains visible during the server's three-second wait. Phase 2 uses authoritative `phaseAgeMs` for its two-second fade and a maximum of 64 short-lived purple particles. A failed/reset phase immediately restores opacity and cancels particles. Opacity has a visible floor until the server actually removes stock. A removed ID by itself is never treated as proof of ME success (it can be a hand take); there is no success-only deletion burst.

Core owns the complete GL state/Tessellator scope. The widget supplies transformed framebuffer pixels with bottom-left origin. Closing clears view frames, selection, fade state and particles and closes the instance renderer; no texture/display-list/FBO/world resource is owned by the widget. Reopening a GUI creates a new per-session bridge and view, starting with a full server frame. Vanilla texture-manager resource reload supplies the cat texture normally.

## Refinement contracts and verification boundary

`VendingSceneContracts` covers codec/ownership, quaternion interpolation, inverse-camera OBB picking, full-wall occlusion, door/body presentation timing, cat phases, pause/reduced motion and lifecycle. `NekoOutputStateTest` covers paid-queue delay persistence, conservative reservations, true waiting-stack rear routing and partial-visible group splitting. `VendingNativeModelPolicyTest` is invoked by the scene contract entry point. These are headless contracts, not actual OpenGL model or mouse-input acceptance; consult the final refinement receipt for exact build status and artifact hashes.

The owner explicitly limits this refinement's native runtime check to startup into the main menu and exit. No world entry, dedicated server, gameplay or GUI-interaction testing is required or authorized for this delivery. Those areas remain unverified and are not pending delivery gates. Historical runtime results belong only to their recorded versions and hashes.

## Shape, native model and paid-queue ownership

GTIT explicitly selects Core's optional ORIENTED_BOX contact mode. Core's old two-argument constructor and default-OFF lifecycle remain unchanged. Blocks/audited volumetric models retain volume proxies; ordinary item sprites use thin native extrusion, not camera-facing billboards. GregTech custom rendering follows the pinned exact-class policy in `VendingNativeModels` / `VendingNativeModelPolicy`; unknown custom renderers retain native INVENTORY fallback.

Old saved sprite depth changes record a machine-owned `migrationSettlePending` event without enabling physics during restore. The next owner tick consumes it only if physics is allowed, settles finitely, and never changes actual stock; disabled pending events survive saves. Modern settled saves do not auto-activate. See Core `docs/miaovision/item-physics-20261027.md` for the independent solver, read-only ItemPhysic provenance and GPL-3.0 no-copy boundary.

Lottery prizes reserve worst-case capacity before payment and enter the persistent machine queue immediately. Animation delays only slot delivery; no volatile delayed task holds a reward copy, and no automatic backpack/world-drop/wallet overflow is introduced. Rear routing counts actual waiting stacks across batches, not visible settled stock; if a grouped batch is already partially visible, only pending identities split to the rear. The 100 output slots and original queue remain the only item owners.

## Round 2 scene and ME contract (2.0.11 candidate)

This section describes the 2.0.11 / Core 0.1.26 implementation. Compilation and the full headless check passed (72 tasks). Exact-artifact startup is recorded separately in the PR and delivery receipts. Earlier 2.0.10 / 0.1.25 evidence remains historical and is not transferred to these edits. Native runtime scope remains main-menu startup and normal Quit only; world/server/gameplay/GUI/ME interaction tests are outside scope, not pending delivery gates.

### Lighting, geometry and camera

GTIT opts into Core's new `SceneModelResolver.dioramaItemLighting()` capability. Core defaults it to false for existing consumers. The fixed, cached item-light rig is established before body rotations/nonuniform scaling, preserving readable back faces without replacing native sprites/blocks/custom material renderers with fullbright rectangles. Native item/glint/custom fallback and complete GL state restoration remain required.

Native **3D block-mesh** bodies use a **.78 scene-unit edge** (half extent .39) consistently for drawing, collision and picking. Vanilla sprite-only block items such as torches, saplings and flowers remain **.54 × .54 × .054** thin sprites; audited special GT volumetric proxies remain .44. Unknown/modded inventory rendering retains its native fallback rather than being declared a universally known 3D mesh. Enlarging blocks does not turn every custom GT item into a block. Saved-shape migration must preserve owned inventory and remain an explicit GTIT event, never Core restore/render auto-enabling simulation.

Default view and Reset share the **20-degree left** view convention through `VendingSceneViewDefaults`. Full-height walls and the existing cat are retained; camera rotation is display-only and must agree with picking/occlusion.

### Enabling ME over stock already dispensed

Enabling ME output also admits eligible existing real output slots, without replaying delivery or making another item copy authoritative. Admission is idempotent by slot/drop/generation. Countdown groups follow the stable **visual dropId**, with **100 ms between groups**; slots belonging to one visual body share presentation timing. Network work remains on the machine's server thread and is bounded to **one network attempt per machine per tick**, including unsuccessful acceptance probes.

Each visible group retains a full **3-second hold plus 2-second fade** before an attempt. A genuinely later visible member cannot inherit an already-aged group timer and skip its hold/fade; the group must restart safely when needed. Immediate-visible and hidden-then-visible late-join regression cases passed. Failed/partial transfer restores the remaining visible stock and restarts its group; only an actual accepted quantity leaves the sole output inventory. Disconnect/disable cancels presentation work without deleting items. Future stagger delays and elapsed time must survive save/load without turning clock rollback into a long frozen queue.

This is bounded per-tick work, not an asynchronous AE worker, TPS benchmark or actual ME-network gameplay acceptance claim.

## 2.0.17 抽屉猫、专用 ME 仓与 FEED 交接

本节记录 GTIT v2.0.17 的最新猫动画和 FEED 入口。此前 v2.0.16 的探头、登台和碰爪动作属于历史版本；当前不再走近或碰爪。红猫从左抽屉背后探头、蹬腿跳上实心台面够币；约10.3秒后黑白猫从右抽屉背后探出，红猫转头受惊跳回，黑白猫回望并看向机器外侧后跳下。猫图使用 Minecraft 1.7.10 ModelOcelot 几何/UV和jar纹理离线预渲染；贴图为24秒、240帧、10fps，不创建实时实体。没有黑色槽口。

金币仍按原生49px绘制和轨迹排列。横排质量要求为高差不超过6px、横距至少30px；三币排高差不超过9px、相邻横距至少24px。240帧横排满足数量由114降至33，三币排由24降至0；连续3帧同对由143降至22，近重叠由58降至53，最小币alpha宽38px。

FEED进入卷帘门时，保存姿势与WAIT姿势相同，入口样本先隐藏，首个运动样本后连续显示，避免约200ms停顿。协议、Core、server与库存/ME时钟/路线不变。

多方块仍为宽5×高4×深2、39个专用外壳ME位、最多1仓。v2.0.17仅接受专用猫猫木壳与专用ME仓；原VM壳/meta 2742仓不再用于成型。旧数据不自动迁移，需手动转换，旧FEED、库存与队列格式保持。

结构与素材的定向检查已部分通过；NEI完整交互及实际Minecraft结构/界面验收仍在进行。未测试非空AE解码，不宣称ME网络验收。

## 2.0.13 本轮原生打磨状态

**历史范围说明：** 前文各节描述其标题所标明的早期实现与验证边界，包括 2.0.11 候选状态；其中的构建或运行记录只适用于对应版本，不构成本轮 GTIT 2.0.13 的验收结果。

本轮 VendingRoomArt 移除了卷帘后背板和条纹实体，并增加随门打开出现的叠加暖光与渐隐地面光束；这些是原生绘制效果，不创建 box 或拾取代理。NekoOutputScene 改为同一扇门完全打开后放行当轮所有已就绪物品，后方使用 8 列×4 行（32 格）、4 个浅层，间距为 0.82。旧 laneCooldown 字段仍读写。MTENekoVendingMachineV2 每轮处理最多 100 个真实输出槽中的已就绪栈，保留原付费队列 readyAt 与满槽停止行为，同轮只播放一次音效。

抽奖、交易、签到和邮件界面完成本轮原生视觉调整。服务端抽奖结果、原时序、停格索引和奖品品级保持不变。Core、依赖、通信协议、玩法及输出库存所有权未变。

状态回归覆盖同批 17 个物理 drop 的同步出场、相同阶段年龄和中途存读不重播；轮盘几何回归覆盖圆周布局与 6,160 次缩放后命中投影。这些是无头契约，不能替代实际游戏中的画面、GL 或输入验收。电视机外形仅作为独立 HTML 设计概念，原生机器结构仍沿用既有定义。

## 2.0.15 门光与抛射

卷帘门光改为随开度扩展的亮门洞、7 条斜向空间光束、6 层渐隐薄光与增强地面光；光片不写深度，也不创建碰撞或拾取遮挡。局部恢复光照、剔除、混合与独立 alpha 因子、alpha test、深度写入、平滑着色和当前颜色，保留 Core 的渲染目标及程序上下文。

就绪批次仍在门完全打开后同时出场。侧门从内唇外侧向内上抛，后门向前上抛；FEED 阶段使用保存的姿态和速度逐 tick 推进重力弧线。释放时由 Core 接管碰撞、阻尼与落地，并且该 tick 只推进一次。旧 FEED 存读继续原位置和速度，不按新发射点重新播放；关闭物理时仍沿用原有配送/落槽语义，不暗中开启或推进 Core 仿真。库存、身份、路线、ME 时钟与原数据格式不变。

## 2.0.16 入口连续显示

FEED age 0 与 WAIT 发射位相同，原先客户端立即显示并对相同位置补间，造成一个 200ms 同步间隔的悬停。现在该入口样本暂不显示，收到首个已移动样本后从入口位置连续补间。首次完整打开界面仍恢复权威快照，减少动态选项直接显示目标；暂停、身份、库存、服务端物理、同步协议和 ME 时钟不变。三条入口路线、显示交接、释放、暂停及重新打开由无头契约验证；实际游戏手感仍需用户试用。
