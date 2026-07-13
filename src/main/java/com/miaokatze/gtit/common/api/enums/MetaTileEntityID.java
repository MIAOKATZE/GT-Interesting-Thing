package com.miaokatze.gtit.common.api.enums;

import com.miaokatze.gtit.config.Config;

/**
 * 元机器实体 (MTE) ID 枚举
 * <p>
 * 模仿 NH-Utilities 的风格，为每个机器分配全局唯一的整数 ID。
 * 这种集中管理的方式可以有效避免与其他模组的机器 ID 发生冲突。
 * <p>
 * 最终 ID 计算公式：BASE (14600) + Config.metaIdOffset (配置偏移量) + relativeId (枚举内相对 ID)
 */
public enum MetaTileEntityID {

    // --- 猫猫售货机 ---
    // V1 枚举已删除：v1.6.19 未发布，无 V2 方块被放置，V2 直接接管 V1 的 ID 14610
    /**
     * 猫猫售货机 (独立化版本，继承 GT5U 的 MTEEnhancedMultiBlockBase，完全脱离 VM 模组)
     * relativeId=10 对应全局 ID 14610，继承自原 V1，确保旧存档中方块不丢失
     */
    NEKO_VENDING_MACHINE_V2(10),

    ;

    // 最终计算出的全局唯一 ID
    public final int ID;

    // ID 基准值，用于避免与其他模组的机器 ID 冲突
    private static final int BASE = 14600;

    /**
     * 构造函数：根据相对 ID 计算全局 ID
     * 
     * @param relativeId 枚举项在列表中的相对索引
     */
    MetaTileEntityID(int relativeId) {
        this.ID = BASE + Config.metaIdOffset + relativeId;
    }
}
