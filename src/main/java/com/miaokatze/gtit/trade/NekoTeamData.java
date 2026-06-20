package com.miaokatze.gtit.trade;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.gtnhlib.teams.ITeamData;
import com.gtnewhorizon.gtnhlib.teams.Team;
import com.gtnewhorizon.gtnhlib.teams.TeamDataCopyReason;
import com.miaokatze.gtit.main.GTInterestingThing;

/**
 * 猫猫币团队数据
 * 实现 GTNHLib Teams API 的 ITeamData 接口
 * 存储团队共享的猫猫币钱包
 * <p>
 * 仅团队钱包模式，无个人钱包数据
 */
public class NekoTeamData implements ITeamData {

    public static final String ID = "GTIT";

    private final NekoWallet wallet = new NekoWallet();

    /**
     * 获取团队共享钱包
     */
    public NekoWallet getWallet() {
        return wallet;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagCompound walletTag = wallet.writeToNBT();
        tag.setTag("wallet", walletTag);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("wallet")) {
            wallet.readFromNBT(tag.getCompoundTag("wallet"));
        }
    }

    /**
     * 团队合并时调用
     * 将被合并团队的钱包余额合并到当前团队
     */
    @Override
    public void mergeData(Team consumed, Team surviving, ITeamData oldTeamData) {
        if (oldTeamData instanceof NekoTeamData) {
            NekoTeamData other = (NekoTeamData) oldTeamData;
            NekoWallet otherWallet = other.getWallet();
            // 合并所有猫猫币余额
            for (String currencyId : otherWallet.getCurrencyIds()) {
                int amount = otherWallet.getCount(currencyId);
                if (amount > 0) {
                    wallet.addCount(currencyId, amount);
                }
            }
            GTInterestingThing.LOG.info("猫猫币团队钱包合并完成");
        }
    }

    /**
     * 玩家转移团队时调用
     * 仅团队钱包模式，无个人数据需要转移
     */
    @Override
    public void copyData(Team prevTeam, Team newTeam, UUID playerId, ITeamData prevTeamData,
        TeamDataCopyReason reason) {
        // 仅团队钱包，无个人数据迁移
    }
}
