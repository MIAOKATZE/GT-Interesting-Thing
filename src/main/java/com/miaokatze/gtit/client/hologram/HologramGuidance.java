package com.miaokatze.gtit.client.hologram;

/** A single contextual next step, shared by the planner and blueprint surfaces. */
final class HologramGuidance {

    private HologramGuidance() {}

    static String next(HologramState state, int selected, boolean blueprints, boolean pending) {
        if (pending) return "等待服务端校验…";
        if (state.data.getBoolean("areaSelecting")) return "左键选第一点，右键选第二点；Alt 使用玩家坐标；Shift+Alt+右键完成";
        if (state.data.getBoolean("blueprintAwaitingConfirmation")) return "检查材料后确认，进入世界预览；再次确认执行施工";
        if (HologramPlacementConfirmation.forceReview(state.data, state.data.getLong("planRevision")))
            return "缺料：再次确认仅放已有；编辑规划或更换材料来源后重新确认";
        if (state.data.getInteger("job") == 1) return "正在施工；可暂停或取消";
        if (state.data.getInteger("job") == 2) return "施工已暂停；确认继续或取消";
        if (blueprints) return "选择蓝图查看预览；编辑返回结构；放置先确认材料";
        if (state.cells.isEmpty()) return "右键控制器选择结构，或 Shift+Alt+右键框选任意区域";
        if (selected >= 0) return "Ctrl 多选 · Shift 两点立体框选 · G 移动轴 · Ctrl+C / V 复制粘贴";
        if (state.data.getBoolean("virtualPlacement")) return "Alt+右键锁定位置；R 旋转；确认施工";
        return "点击方块编辑；点击结构淡框边缘选择整个结构";
    }

    static int recommended(HologramState state, int selected, boolean blueprints, boolean confirmation) {
        if (confirmation) return 55;
        if (state.data.getInteger("job") == 2) return 51;
        if (blueprints) return state.data.getString("selectedPresetId")
            .isEmpty() ? 149 : 141;
        if (state.data.getBoolean("virtualPlacement") && !state.data.getBoolean("virtualLocked")) return 135;
        if (selected >= 0) return 146;
        if (state.data.getBoolean("target") && !state.cells.isEmpty()) return 50;
        return 149;
    }

    static int accent(int id, HologramState state, int selected, boolean blueprints, boolean confirmation) {
        if (id == 52 && (state.data.getInteger("job") == 1 || state.data.getInteger("job") == 2)
            || id == 54 && confirmation
            || id == 136
            || id == 151) return 0xef7b7b;
        if (id != recommended(state, selected, blueprints, confirmation)) return 0;
        return id == 50 || id == 55 || id == 51 || id == 135 ? 0x80efaa : 0x68dded;
    }

    static int modalAccent(int id, int confirmation, int cancellation) {
        return id == confirmation ? 0x80efaa : id == cancellation ? 0xef7b7b : 0;
    }
}
