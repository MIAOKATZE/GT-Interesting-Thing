package com.miaokatze.gtit.lottery;

/**
 * 抽奖动画控制器
 */
public class LotteryAnimationController {

    public enum AnimationState {
        IDLE,
        SPINNING,
        REVEALING,
        FINISHED
    }

    private AnimationState state = AnimationState.IDLE;
    private int tickCount = 0;

    public void startAnimation() {
        // TODO: v1.6.4 实现
    }

    public void onUpdate() {
        // TODO: v1.6.4 实现
    }

    public boolean isFinished() {
        // TODO: v1.6.4 实现
        return false;
    }

    public void reset() {
        // TODO: v1.6.4 实现
    }

    public AnimationState getState() {
        return state;
    }
}
