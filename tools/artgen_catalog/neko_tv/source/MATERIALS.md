# 猫猫售货机正面素材

这是离线预渲染再切片的资源流程。运行时仍只消费原有 `NEKOTV_FULL_OVERLAY` / `NEKOTV_FULL_OVERLAY_ACTIVE` 的 19 张方块贴图，不创建实体，也不调用世界或 GUI 猫渲染器。

## 来源与复现

- `cat-model.json` 由 `extract_cat_model.py` 读取本项目 Gradle 生成的 Minecraft 1.7.10 `ModelOcelot.java` 构造器提取：8 个部件、11 个 ModelBox，包含原始 pivot、box origin/size、纹理 offset。JSON 记录 ModelOcelot、ModelBox、TexturedQuad 三份实际源文件的 SHA-256。
- 猫使用原版 `red.png` / `black.png`，从 `build/rfg/mcp_patched_minecraft-sources.jar` 读取，并核对条目 SHA-256；没有手绘替代猫或把 Minecraft 源图另存进 mod。首次 clone 先执行本项目常规 Gradle 构建以生成该 jar。修改模型来源时再运行 `python tools/artgen_catalog/neko_tv/source/extract_cat_model.py`。
- 通用 CPU 透视、原 ModelBox UV、最近邻取样、深度缓冲和图层复合来自独立工具包 `../贴图复合渲染/src/texture_compositor`。`cat_scene.py` 只保留本场景的相机、原版站/坐姿和动作编排。
- `miao_coin.png` 继续使用项目原图的四帧，烘焙前核对 SHA-256。`materials.py` 在 16px 原生分辨率绘制木纹、金边，并保留控制器原电子面板/红绿指示像素；旧 NEKOVM 资源不覆盖。
- 在仓库根目录运行 `python -B tools/artgen_catalog/neko_tv/bake.py`。依赖 Pillow、NumPy、Python Playwright 与已安装 Microsoft Edge；浏览器仅在烘焙时合成静态 SVG 背景、金币和预渲猫帧，不是游戏实时 renderer。

## 布局与动作

画布 160×128，5×4 格，每格 32px；按从上到下、从左到右输出 19 片，跳过 `(row=3,col=2)` 的原生控制器。该格整周期 RGBA alpha 为零，预览另用新的木框控制器前脸合成。

上三行保留 49px 金币（8 条循环轨迹），金币严格裁剪至 y<96。底行左右各两格成为带金色把手的木抽屉。两只原版模型猫先后从暗色开口探出、爬上台沿、转头张望，走近抬起前爪互动，再退回并缩入抽屉；头、前后腿和尾巴均有独立几何旋转。柜台前板最后合成，遮挡仍在抽屉内的身体，猫不会覆盖控制器。

固定循环 24 秒、10fps、240 帧，`.mcmeta` 每帧 2 tick，与玩家、订单和机器业务状态无关。末端重复帧不输出。旧 6 片 API/资源不改，现有 19 片 API/顺序不改，仅替换其素材内容。

## 核验输出

`build/neko-tv-preview/verification.json` 记录逐片 PNG/frame hash、模型/贴图来源、240 帧回拼一致、控制器透明、底行金币开关差分一致、抽屉前板遮挡及 24 秒循环一致。`screen.png`、`screen.gif`、`contact-sheet.png` 和 `key-*.png` 用于目检。这些是资源合成预览，不是 Minecraft 实拍或结构/运行测试。
