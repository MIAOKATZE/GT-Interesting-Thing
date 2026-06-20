package com.miaokatze.gtit.trade;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 猫猫币钱包
 * 独立于 VM 的 Wallet 系统，使用 HashMap 存储猫猫币余额
 * 每个 UUID 对应一个 NekoWallet 实例
 */
public class NekoWallet {

    private final Map<String, Integer> balances = new HashMap<>();

    /**
     * 获取指定猫猫币的余额
     */
    public int getCount(String currencyId) {
        return balances.getOrDefault(currencyId, 0);
    }

    /**
     * 增加猫猫币余额（amount 可为负数表示扣除）
     */
    public void addCount(String currencyId, int amount) {
        int current = getCount(currencyId);
        int newAmount = current + amount;
        if (newAmount < 0) newAmount = 0;
        balances.put(currencyId, newAmount);
    }

    /**
     * 重置指定猫猫币余额为 0
     */
    public void resetCount(String currencyId) {
        balances.put(currencyId, 0);
    }

    /**
     * 重置所有余额
     */
    public void resetAll() {
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
