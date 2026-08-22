package com.miaokatze.gtit.client.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Icon;
import com.cleanroommc.modularui.drawable.UITexture;

/**
 * 钱包模式枚举
 * <p>
 * 完美复刻 VM mod 的 {@code com.cubefury.vendingmachine.blocks.gui.WalletMode} 枚举。
 * 用于区分个人钱包和团队钱包两种货币消费模式。
 * <p>
 * <b>TEAM 模式已启用</b>：通过 {@link com.miaokatze.gtit.trade.NekoWalletManager} 对接
 * GTNHLib Teams API，当玩家处于团队中时，{@code NekoWalletManager.getWallet()} 会自动
 * 路由到团队共享钱包（{@link com.miaokatze.gtit.trade.NekoTeamData}）。
 * {@link com.miaokatze.gtit.gui.vm.NekoVMGuiV2#getWalletMode()} 会检测玩家
 * 团队状态并返回对应的钱包模式，带 NoClassDefFoundError 防护（GTNHLib 不可用时降级为 PERSONAL）。
 * <p>
 * 本地化 key 从 {@code vendingmachine.gui.display_wallet_*} 迁移到 {@code gtit.gui.display_wallet_*}。
 *
 * @see NekoGuiTextures#WALLET_PERSONAL
 * @see NekoGuiTextures#WALLET_TEAM
 */
public enum NekoWalletMode {

    /** 个人钱包 - 仅消费玩家个人余额（无团队或 GTNHLib 不可用时使用） */
    PERSONAL("personal", NekoGuiTextures.WALLET_PERSONAL),

    /** 团队钱包 - 消费团队共享余额（通过 NekoWalletManager 自动路由，GTNHLib Teams API 对接） */
    TEAM("team", NekoGuiTextures.WALLET_TEAM);

    /** 钱包模式的内部标识名 */
    private final String mode;

    /** 钱包模式对应的纹理图标 */
    private final Icon texture;

    /**
     * 构造一个钱包模式
     *
     * @param mode    内部标识名（用于本地化 key 拼接）
     * @param texture 对应的 UITexture 纹理（将转换为 Icon）
     */
    NekoWalletMode(String mode, UITexture texture) {
        this.mode = mode;
        this.texture = texture.asIcon();
    }

    /**
     * 获取钱包模式的本地化名称
     * <p>
     * 本地化 key 格式：{@code gtit.gui.display_wallet_<mode>}
     *
     * @return 本地化后的钱包模式名称
     */
    public String getLocalizedName() {
        return IKey.lang("gtit.gui.display_wallet_" + this.mode)
            .toString();
    }

    /**
     * 获取钱包模式对应的纹理图标
     *
     * @return 纹理图标
     */
    public Icon getTexture() {
        return this.texture;
    }
}
