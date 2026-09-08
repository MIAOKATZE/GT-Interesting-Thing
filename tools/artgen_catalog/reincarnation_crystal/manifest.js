// 轮回水晶候选风格集 manifest（viewer.html 只读消费）
// 4 套底形迥异候选 + FINAL 收敛落地款；B/C/FINAL 为动画款，
// 预览为按 mcmeta 帧时换算的 GIF（1 tick=50ms）
const CATALOG = {
  generated: "2026-09-09T00:00:00",
  blocks: [],
  styles: [
    {
      id: "style_a_shard",
      name: "A 六棱晶柱（静态）",
      desc: "清澈蓝白多面晶簇，左受光右背光+横向断口分晶，虹彩顶点品红/青/金+星芒；底形=竖向棱柱群",
      items: [
        { id: "reincarnation_crystal", file: "styles/style_a_shard/reincarnation_crystal.png", letter: "A", hue: "blue", name: "轮回水晶" }
      ]
    },
    {
      id: "style_b_ring",
      name: "B 轮回法环（动画 8帧@0.1s）",
      desc: "蓝白圆环受光弧固定，虹彩彗星流光（白头-青-品红-金尾）绕环飞驰，星尘常驻；底形=环形",
      items: [
        { id: "reincarnation_crystal", file: "styles/style_b_ring/reincarnation_crystal.gif", letter: "B", hue: "purple", name: "轮回水晶" }
      ]
    },
    {
      id: "style_c_spiral",
      name: "C 轮回双螺旋（动画 4帧 ping-pong@0.15s）",
      desc: "紫白双臂向心汇聚，呼吸明暗+向心脉冲+虹彩晶砂渐次点亮，白核收束；底形=涡旋",
      items: [
        { id: "reincarnation_crystal", file: "styles/style_c_spiral/reincarnation_crystal.gif", letter: "C", hue: "purple", name: "轮回水晶" }
      ]
    },
    {
      id: "style_d_orb",
      name: "D 晶球核（静态）",
      desc: "蓝紫玻璃球，左上高光十字+右下透射热点，白核+八角虹彩光环+气泡微晶；底形=正球",
      items: [
        { id: "reincarnation_crystal", file: "styles/style_d_orb/reincarnation_crystal.png", letter: "D", hue: "purple", name: "轮回水晶" }
      ]
    },
    {
      id: "final_crystal",
      name: "FINAL 金白自旋双螺旋（落地款 · 动画 24帧@0.05s = 1.2s/圈）",
      desc: "C 基底收敛：金-白-金渐变主宰（外深金-中暖白闪光带-内回金），整枚螺旋刚体自旋 15度/帧线性循环，受光主臂/背光副臂亮度差，白核收束+极微量品红/青珠光晶砂；底形=涡旋（已落地 assets/gtit/textures/items/reincarnation_crystal.png）",
      items: [
        { id: "reincarnation_crystal", file: "styles/final/reincarnation_crystal.gif", letter: "F", hue: "gold", name: "轮回水晶" }
      ]
    }
  ]
};
