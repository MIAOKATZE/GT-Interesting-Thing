package com.miaokatze.gtit.crossmod.miaogtnh;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.crossmod.bq.BqCompat;
import com.miaokatze.gtit.crossmod.bq.BqQuestInjector;

/**
 * GTIT 包注入 + MIAO 空包清理保险（空清单包触发注入器剪枝，清空老世界已注入的 MIAO 任务/任务线）。
 */
public class MiaoGtnhHost {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** GTIT 自身任务包资产根（jar 内，必须以 / 结尾） */
    private static final String GTIT_PACK_ROOT = "assets/gtit/bqquests/";
    /** MIAO-GTNH 综合任务包资产根（jar 内，必须以 / 结尾） */
    private static final String MIAO_PACK_ROOT = "assets/gtit/bqquests/miao/";

    /**
     * serverStarting 挂载点：BQ 任务包注入。
     */
    public static void onServerStarting() {
        if (BqCompat.isBqLoaded()) {
            BqQuestInjector.inject(GTIT_PACK_ROOT);
            // 空包哨兵：借注入器剪枝清空老世界残留的 MIAO 任务与任务线
            BqQuestInjector.inject(MIAO_PACK_ROOT);
        }
    }
}
