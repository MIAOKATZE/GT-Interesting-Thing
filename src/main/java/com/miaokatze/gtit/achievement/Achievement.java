package com.miaokatze.gtit.achievement;

/**
 * 成就定义
 */
public class Achievement {

    private String id;
    private String name;
    private String description;
    private AchievementType type;
    private AchievementCategory category;
    private AchievementCondition condition;
    private String rewardCurrency;
    private int rewardAmount;
    private String iconItemId;
    private int iconMeta;
    private boolean hidden;
    private int sortOrder;

    public Achievement() {}

    public Achievement(String id, String name, String description, AchievementType type, AchievementCategory category,
        AchievementCondition condition, String rewardCurrency, int rewardAmount, String iconItemId, int iconMeta,
        boolean hidden, int sortOrder) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.category = category;
        this.condition = condition;
        this.rewardCurrency = rewardCurrency;
        this.rewardAmount = rewardAmount;
        this.iconItemId = iconItemId;
        this.iconMeta = iconMeta;
        this.hidden = hidden;
        this.sortOrder = sortOrder;
    }

    public boolean hasValidReward() {
        return rewardCurrency != null && !rewardCurrency.isEmpty() && rewardAmount > 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public AchievementType getType() {
        return type;
    }

    public AchievementCategory getCategory() {
        return category;
    }

    public AchievementCondition getCondition() {
        return condition;
    }

    public String getRewardCurrency() {
        return rewardCurrency;
    }

    public int getRewardAmount() {
        return rewardAmount;
    }

    public String getIconItemId() {
        return iconItemId;
    }

    public int getIconMeta() {
        return iconMeta;
    }

    public boolean isHidden() {
        return hidden;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
