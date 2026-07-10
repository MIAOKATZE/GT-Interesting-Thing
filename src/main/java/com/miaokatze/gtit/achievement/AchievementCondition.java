package com.miaokatze.gtit.achievement;

/**
 * 成就条件模型
 */
public class AchievementCondition {

    private String conditionType;
    private int targetValue;
    private String operator;
    private String metadata;

    public AchievementCondition() {}

    public AchievementCondition(String conditionType, int targetValue) {
        this.conditionType = conditionType;
        this.targetValue = targetValue;
        this.operator = ">=";
        this.metadata = "";
    }

    public AchievementCondition(String conditionType, int targetValue, String operator, String metadata) {
        this.conditionType = conditionType;
        this.targetValue = targetValue;
        this.operator = operator;
        this.metadata = metadata;
    }

    public boolean isMet(int currentValue) {
        // TODO: v1.6.5 实现
        return false;
    }

    public int getProgressPercent(int currentValue) {
        // TODO: v1.6.5 实现
        return 0;
    }

    public String getConditionType() {
        return conditionType;
    }

    public int getTargetValue() {
        return targetValue;
    }

    public String getOperator() {
        return operator;
    }

    public String getMetadata() {
        return metadata;
    }
}
