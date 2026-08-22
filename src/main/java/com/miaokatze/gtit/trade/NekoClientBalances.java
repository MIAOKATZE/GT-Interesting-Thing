package com.miaokatze.gtit.trade;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端钱包余额缓存（O2-B01）
 * <p>
 * 原先作为 {@code LotteryClientData.balances} 寄居在抽奖域客户端缓存中、
 * 由 trade 域的网络包跨域写入——O2-B01 余额通道自立（{@code WalletNetworkManager}，
 * 通道 {@code gtit_wallet}）后，缓存随通道迁入 trade 包，lottery 通道回归纯抽奖。
 * <p>
 * 写入方（均为 S→C，客户端主线程）：
 * <ul>
 * <li>{@code WalletBalancePacket}（余额轻量推送：脏标记节流冲刷/登录全量补推）</li>
 * <li>{@code LotterySyncPacket}（抽奖全量同步附带余额维度——服务端构建时复用钱包数据）</li>
 * </ul>
 * 读取方：抽奖页余额显示（{@code LotteryGui}）等客户端 GUI。
 * <p>
 * 服务端侧不会收到同步包，本类在服务端始终保持空数据（读到默认值 0，
 * 不影响服务端逻辑——钱包判定完全由 {@link NekoWalletManager} 在服务端权威执行）。
 */
public final class NekoClientBalances {

    /** 余额表（currencyId → 数量；volatile 引用供 GUI 线程无锁读取，写侧整表替换） */
    private static volatile Map<String, Integer> balances = new LinkedHashMap<>();

    private NekoClientBalances() {}

    /**
     * 整表刷新余额维度（仅覆盖 balances，不动其他缓存）
     *
     * @param newBalances 钱包余额（currencyId → 数量；null 忽略）
     */
    public static synchronized void updateBalances(Map<String, Integer> newBalances) {
        if (newBalances != null) {
            balances = new LinkedHashMap<>(newBalances);
        }
    }

    /**
     * 指定货币的钱包余额（随同步包刷新；未同步前为 0）
     */
    public static int getBalance(String currencyId) {
        if (currencyId == null) return 0;
        Integer amount = balances.get(currencyId);
        return amount == null ? 0 : amount;
    }
}
