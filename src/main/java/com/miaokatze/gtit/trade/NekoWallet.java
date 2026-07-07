package com.miaokatze.gtit.trade;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 猫猫币钱包
 * 独立于 VM 的 Wallet 系统，使用 HashMap 存储猫猫币余额
 * 每个 UUID 对应一个 NekoWallet 实例
 */
public class NekoWallet {

    // ConcurrentHashMap：多线程（交易主线程、GUI changeListener、团队数据合并）可能并发访问同一钱包
    private final Map<String, Integer> balances = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 获取指定猫猫币的余额
     */
    public synchronized int getCount(String currencyId) {
        return balances.getOrDefault(currencyId, 0);
    }

    /**
     * 增加猫猫币余额（amount 可为负数表示扣除）
     */
    public synchronized void addCount(String currencyId, int amount) {
        int current = getCount(currencyId);
        int newAmount = current + amount;
        if (newAmount < 0) newAmount = 0;
        balances.put(currencyId, newAmount);
    }

    /**
     * 原子地检查余额并扣减
     * <p>
     * 解决团队钱包双重消费竞态：两个团队成员近乎同时下单时，
     * "余额检查 + addCount(-cost)" 两步操作之间可能被对方插入，
     * 导致两人都通过检查后余额被扣到 0、但两人都拿到产物。
     * 本方法将检查与扣减合并为单个 synchronized 原子操作。
     *
     * @param currencyId 货币ID
     * @param amount     扣减数量（正数）
     * @return true=余额足够并已扣减；false=余额不足，未扣减
     */
    public synchronized boolean tryDeduct(String currencyId, int amount) {
        if (amount <= 0) return true;
        int current = getCount(currencyId);
        if (current < amount) return false;
        balances.put(currencyId, current - amount);
        return true;
    }

    /**
     * 重置指定猫猫币余额为 0
     */
    public synchronized void resetCount(String currencyId) {
        balances.put(currencyId, 0);
    }

    /**
     * 重置所有余额
     */
    public synchronized void resetAll() {
        balances.clear();
    }

    /**
     * 保存到 NBT
     */
    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : balances.entrySet()) {
            nbt.setInteger(entry.getKey(), entry.getValue());
        }
        return nbt;
    }

    /**
     * 从 NBT 读取
     * 注意：getNekoCurrencyIds() 只返回字符串常量，不依赖 init() 状态，可安全在团队数据加载时调用
     */
    public void readFromNBT(NBTTagCompound nbt) {
        balances.clear();
        for (String currencyId : NekoCurrencyRegistrar.getNekoCurrencyIds()) {
            if (nbt.hasKey(currencyId)) {
                balances.put(currencyId, nbt.getInteger(currencyId));
            }
        }
    }

    /**
     * 获取所有货币 ID
     */
    public java.util.Set<String> getCurrencyIds() {
        return balances.keySet();
    }

    /**
     * 复制钱包
     */
    public NekoWallet copy() {
        NekoWallet wallet = new NekoWallet();
        wallet.balances.putAll(this.balances);
        return wallet;
    }
}
