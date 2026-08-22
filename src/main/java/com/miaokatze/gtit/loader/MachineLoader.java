package com.miaokatze.gtit.loader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.miaokatze.gtit.register.MultiblockMachineRegistrar;

/**
 * 机器加载器
 * 负责初始化并执行所有元机器实体 (MTE) 的注册逻辑。
 * O2-02: 移除从未兑现的 RegistrationManager 三层转发（创建 registrar→addRegistrar→立即 registerAll
 * 的自反调用，全项目仅此一个注册任务），改为直接执行多方块机器注册器。
 * 执行时机不变：仍由 CommonProxy 挂入 GregTech sAfterGTPreload 队列消费，注册 ID 不变。
 */
public class MachineLoader {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /**
     * 初始化所有机器
     * 该方法应在模组预初始化 (PreInit) 或初始化 (Init) 阶段调用
     */
    public static void initMachines() {
        // O2-02: 直调注册器；保留原 RegistrationManager.registerAll 的异常隔离语义
        try {
            new MultiblockMachineRegistrar().registerAll();
        } catch (Throwable t) {
            // 记录错误但不上抛，防止模组因局部问题完全崩溃（外层 Runnable 另有兜底 catch）
            LOG.error("机器注册任务执行失败", t);
        }
    }
}
