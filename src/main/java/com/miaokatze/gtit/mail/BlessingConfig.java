package com.miaokatze.gtit.mail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.miaokatze.gtit.config.ConfigMigrationUtil;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * 自动祝福邮件配置（v1.7.6 G5；v1.7.7 G4 存储结构重构）
 * <p>
 * 管理节日祝福表与生日祝福模板的加载、保存与查询，
 * 配置文件路径: {@code config/gtit/mail/blessing_config.json}。
 * <p>
 * 兼容：加载时若新路径缺失且旧文件 {@code config/gtit/blessing_config.json} 存在，
 * 则整体迁移到新路径，旧文件重命名为 {@code .bak} 保留。
 * 结构参照 {@link com.miaokatze.gtit.signin.OnlineTimeConfig}（Gson 序列化，缺省生成默认配置）：
 * <ul>
 * <li>{@code festivals}：节日祝福表（名称 / MM-dd 固定日期 / 邮件标题 / 正文 / 附件物品列表 / 猫猫币 ID+数量）</li>
 * <li>{@code birthday}：生日祝福模板（标题 / 正文 / 附件物品列表 / 猫猫币；日期来自玩家自配生日）</li>
 * <li>{@code sender}：祝福邮件发件人显示名（默认「猫猫售货机」）</li>
 * </ul>
 * 默认节日（v1.7.8 用户确认口径）：公历节日 = 元旦 / 情人节 / 妇女节 / 愚人节 /
 * 劳动节 / 儿童节 / 教师节 / 国庆 / 万圣节前夜 / 圣诞；农历节日 = 春节 / 元宵 /
 * 端午 / 七夕 / 中秋 / 重阳 / 腊八 / 除夕——农历节日由 {@code lunar} 字段
 * （"月-日"，除夕 = "12-L" 腊月最后一天）表达，触发时经
 * {@code lunar.LunarCalendar} 换算当天农历后比对，无需管理员逐年改日期；
 * 旧配置（无 {@code lunar} 字段）仍按 {@code month_day} 固定公历触发，完全兼容。
 * 每节 = 食物附件 + 少量猫猫币；生日 = minecraft:cake×1。
 * <p>
 * <b>发放形式（v1.7.6 用户确认）</b>：猫猫币以附件<b>物品</b>形式随邮件发放
 * （玩家领取后自行投币入钱包），不直接写入钱包。
 * <p>
 * <b>编辑模式可编辑（用户补充要求）</b>：编辑模式下邮件页「祝福预设」面板
 * 可查看/修改本配置全部预设（节日每节 + 生日模板），保存经 C→S 由服务端
 * 权威写回本 JSON 并热重载（见 {@code NekoEditActionHandler#saveBlessing}）。
 * <p>
 * <b>双端口径</b>：本类双端加载（客户端作编辑面板字段预览，与签到编辑同口径），
 * 权威判定与落盘均在服务端。
 */
public class BlessingConfig {

    /** 统一 logger（O2-B02 去中心化：与主类同用 "gtit" logger 名，日志过滤口径不变） */
    private static final Logger LOG = LogManager.getLogger("gtit");

    /** 新配置文件路径（相对游戏根目录） */
    private static final String CONFIG_PATH = "config/gtit/mail/blessing_config.json";
    /** 旧配置文件路径（v1.7.7 G4 兼容迁移用） */
    private static final String LEGACY_CONFIG_PATH = "config/gtit/blessing_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    /** 祝福邮件发件人显示名（默认「猫猫售货机」） */
    private static String sender = "猫猫售货机";
    /** 节日祝福表（按配置顺序） */
    private static List<FestivalBlessing> festivals = new ArrayList<>();
    /** 生日祝福模板 */
    private static BirthdayBlessing birthday = new BirthdayBlessing();

    // ==================== JSON 结构（兼作运行时模型，编辑保存路径直接改字段后落盘） ====================

    /** 配置根对象（对应 blessing_config.json 根） */
    public static class ConfigData {

        /** 说明文字（仅展示用途，保存时原样保留） */
        @SerializedName("comment")
        public String comment = "";
        @SerializedName("sender")
        public String sender = "猫猫售货机";
        @SerializedName("festivals")
        public List<FestivalBlessing> festivals = new ArrayList<>();
        @SerializedName("birthday")
        public BirthdayBlessing birthday = new BirthdayBlessing();
    }

    /** 附件物品条目（"modid:name" + meta + 数量） */
    public static class BlessingItem {

        @SerializedName("item")
        public String item = "";
        @SerializedName("meta")
        public int meta = 0;
        @SerializedName("amount")
        public int amount = 1;

        public BlessingItem() {}

        public BlessingItem(String item, int meta, int amount) {
            this.item = item == null ? "" : item;
            this.meta = Math.max(0, meta);
            this.amount = Math.max(1, amount);
        }

        /**
         * 解析为物品堆（物品 ID 无法解析时返回 null，调用方跳过）
         */
        public ItemStack toItemStack() {
            if (item == null || item.isEmpty()) return null;
            String[] parts = item.split(":", 2);
            if (parts.length != 2) return null;
            Item found = GameRegistry.findItem(parts[0], parts[1]);
            if (found == null) {
                LOG.warn("祝福附件物品不存在: {}", item);
                return null;
            }
            return new ItemStack(found, Math.max(1, amount), Math.max(0, meta));
        }
    }

    /** 节日祝福条目（lunar 非空按农历触发，否则按 MM-dd 固定公历日期触发） */
    public static class FestivalBlessing {

        /** 节日名称（展示/防重键用） */
        @SerializedName("name")
        public String name = "";
        /** 触发日期（"MM-dd" 固定公历；lunar 非空时不参与比对，供编辑面板展示） */
        @SerializedName("month_day")
        public String monthDay = "";
        /**
         * 农历触发日期（"月-日"，如 {@code "1-1"} = 正月初一、{@code "8-15"} = 八月十五；
         * {@code "12-L"} = 腊月最后一天（除夕），L 由换算表取腊月天数判定）。
         * 空串 = 按公历 {@code month_day} 触发；非空时优先于公历字段。
         * 闰月日（如闰六月）不触发，等正常月。
         */
        @SerializedName("lunar")
        public String lunar = "";
        @SerializedName("title")
        public String title = "";
        @SerializedName("content")
        public String content = "";
        /** 附件物品列表（食物等；不含猫猫币——猫猫币由 currency/currency_amount 表达） */
        @SerializedName("items")
        public List<BlessingItem> items = new ArrayList<>();
        /** 猫猫币 ID（"neko"/"shimmeringNeko"，空串 = 无猫猫币附件） */
        @SerializedName("currency")
        public String currency = "";
        /** 猫猫币数量（作为附件物品发放，不直入钱包） */
        @SerializedName("currency_amount")
        public int currencyAmount = 0;

        public FestivalBlessing() {}

        public FestivalBlessing(String name, String monthDay, String title, String content, List<BlessingItem> items,
            String currency, int currencyAmount) {
            this(name, monthDay, "", title, content, items, currency, currencyAmount);
        }

        /** 带农历触发日期的完整构造（lunar 传空串 = 纯公历节日） */
        public FestivalBlessing(String name, String monthDay, String lunar, String title, String content,
            List<BlessingItem> items, String currency, int currencyAmount) {
            this.name = name == null ? "" : name;
            this.monthDay = monthDay == null ? "" : monthDay;
            this.lunar = lunar == null ? "" : lunar;
            this.title = title == null ? "" : title;
            this.content = content == null ? "" : content;
            if (items != null) this.items = items;
            this.currency = currency == null ? "" : currency;
            this.currencyAmount = Math.max(0, currencyAmount);
        }

        /**
         * 构建邮件附件物品列表（配置物品深拷贝 + 猫猫币物品；调用方可安全修改返回值）
         */
        public List<ItemStack> buildAttachments() {
            List<ItemStack> attachments = new ArrayList<>();
            for (BlessingItem spec : items) {
                if (spec == null) continue;
                ItemStack stack = spec.toItemStack();
                if (stack != null) attachments.add(stack);
            }
            // 猫猫币以附件物品形式发放（v1.7.6 用户确认口径：领取后自行投币入钱包）
            if (currencyAmount > 0 && currency != null && !currency.isEmpty()) {
                ItemStack coin = NekoCurrencyRegistrar.getItemStack(currency, currencyAmount);
                if (coin != null) attachments.add(coin);
            }
            return attachments;
        }
    }

    /** 生日祝福模板（日期来自玩家自配生日 DailySignInData.birthday） */
    public static class BirthdayBlessing {

        @SerializedName("title")
        public String title = "生日快乐！";
        @SerializedName("content")
        public String content = "今天是你的生日，猫猫售货机全体员工祝你生日快乐！";
        @SerializedName("items")
        public List<BlessingItem> items = new ArrayList<>();
        @SerializedName("currency")
        public String currency = "";
        @SerializedName("currency_amount")
        public int currencyAmount = 0;

        /**
         * 构建邮件附件物品列表（同 {@link FestivalBlessing#buildAttachments()}）
         */
        public List<ItemStack> buildAttachments() {
            List<ItemStack> attachments = new ArrayList<>();
            for (BlessingItem spec : items) {
                if (spec == null) continue;
                ItemStack stack = spec.toItemStack();
                if (stack != null) attachments.add(stack);
            }
            if (currencyAmount > 0 && currency != null && !currency.isEmpty()) {
                ItemStack coin = NekoCurrencyRegistrar.getItemStack(currency, currencyAmount);
                if (coin != null) attachments.add(coin);
            }
            return attachments;
        }
    }

    // ==================== 加载 / 保存 ====================

    public static void init() {
        loadConfig();
    }

    /**
     * 加载配置文件；文件不存在或解析失败时使用默认配置并落盘
     * <p>
     * v1.7.7 G4：优先读取新路径；新路径缺失且旧路径存在时，迁移旧文件到新路径，
     * 旧文件重命名为 {@code .bak} 保留。
     */
    public static void loadConfig() {
        Path path = Paths.get(CONFIG_PATH);
        if (!Files.exists(path)) {
            Path legacy = Paths.get(LEGACY_CONFIG_PATH);
            if (Files.exists(legacy)) {
                try {
                    migrateFromLegacy(legacy, path);
                    // 迁移后继续从新路径读取
                } catch (Exception e) {
                    LOG.error("祝福邮件配置从旧路径迁移失败，回退默认配置", e);
                    applyDefaults();
                    saveConfig();
                    return;
                }
            }
        }

        if (Files.exists(path)) {
            try {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                if (data != null) {
                    sender = data.sender == null || data.sender.isEmpty() ? "猫猫售货机" : data.sender;
                    festivals = data.festivals == null ? new ArrayList<>() : data.festivals;
                    birthday = data.birthday == null ? new BirthdayBlessing() : data.birthday;
                    LOG.info("祝福邮件配置已加载（{} 个节日 + 生日模板）", festivals.size());
                    return;
                }
            } catch (Exception e) {
                LOG.error("加载祝福邮件配置失败，使用默认配置", e);
            }
        }
        // 首次运行或加载失败：使用默认配置并落盘
        applyDefaults();
        saveConfig();
    }

    /**
     * 从旧路径迁移配置到新路径（v1.7.7 G4）
     *
     * @param legacyPath 旧配置文件路径
     * @param newPath    新配置文件路径
     */
    private static void migrateFromLegacy(Path legacyPath, Path newPath) throws Exception {
        Files.createDirectories(newPath.getParent());
        String json = new String(Files.readAllBytes(legacyPath), StandardCharsets.UTF_8);
        ConfigData data = GSON.fromJson(json, ConfigData.class);
        if (data != null) {
            Files.write(
                newPath,
                GSON.toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
        }
        // O2-14: 旧文件退役收尾收编 ConfigMigrationUtil.retireLegacyAsBak（语义与日志格式不变）
        ConfigMigrationUtil.retireLegacyAsBak(legacyPath, newPath, "祝福邮件配置", "祝福邮件配置文件");
    }

    /**
     * 将当前内存配置写回 JSON 文件
     */
    public static void saveConfig() {
        try {
            Path path = Paths.get(CONFIG_PATH);
            Files.createDirectories(path.getParent());
            ConfigData data = new ConfigData();
            data.comment = "节日祝福表：lunar 非空（\"月-日\"，\"12-L\"=除夕腊月末日）按农历自动换算触发，"
                + "否则按 month_day 固定公历触发；旧配置缺 lunar 字段按公历，完全兼容";
            data.sender = sender;
            data.festivals = festivals;
            data.birthday = birthday;
            Files.write(
                path,
                GSON.toJson(data)
                    .getBytes(StandardCharsets.UTF_8));
            LOG.info("祝福邮件配置已保存");
        } catch (Exception e) {
            LOG.error("保存祝福邮件配置失败", e);
        }
    }

    /**
     * 热重载配置（编辑保存/指令路径调用，与在线时间配置 reload 同模式）
     */
    public static void reload() {
        loadConfig();
    }

    // ==================== 查询 ====================

    /** 祝福邮件发件人显示名 */
    public static String getSender() {
        return sender;
    }

    /**
     * 设置祝福邮件发件人显示名（编辑保存路径调用，修改后需 {@link #saveConfig()}）
     */
    public static void setSender(String newSender) {
        sender = newSender == null || newSender.isEmpty() ? "猫猫售货机" : newSender;
    }

    /** 节日祝福表（按配置顺序；编辑面板直接持有引用，修改后需 {@link #saveConfig()}） */
    public static List<FestivalBlessing> getFestivals() {
        return festivals;
    }

    /** 生日祝福模板（同上，修改后需 {@link #saveConfig()}） */
    public static BirthdayBlessing getBirthday() {
        return birthday;
    }

    // ==================== 内部辅助 ====================

    /**
     * 默认配置（v1.7.8 用户确认口径）：
     * <ul>
     * <li>公历节日（month_day）= 元旦 / 情人节 / 妇女节 / 愚人节 / 劳动节 / 儿童节 /
     * 教师节 / 国庆 / 万圣节前夜 / 圣诞（外国节日仅加用户拍板的三个）</li>
     * <li>农历节日（lunar，自动换算）= 春节 1-1 / 元宵 1-15 / 端午 5-5 / 七夕 7-7 /
     * 中秋 8-15 / 重阳 9-9 / 腊八 12-8 / 除夕 12-L（腊月最后一天）</li>
     * </ul>
     * 每节 = 食物附件（1-2 件 Vanilla 物品）+ 少量猫猫币；生日 = minecraft:cake×1。
     */
    private static void applyDefaults() {
        sender = "猫猫售货机";
        festivals = new ArrayList<>();

        // ---- 公历节日 ----

        List<BlessingItem> newYearItems = new ArrayList<>();
        newYearItems.add(new BlessingItem("minecraft:bread", 0, 2));
        festivals.add(
            new FestivalBlessing(
                "元旦",
                "01-01",
                "元旦快乐！",
                "新的一年，愿猫猫与你同在。祝元旦快乐！",
                newYearItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        List<BlessingItem> valentineItems = new ArrayList<>();
        valentineItems.add(new BlessingItem("minecraft:golden_carrot", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "情人节",
                "02-14",
                "情人节快乐！",
                "金萝卜甜甜的，像今天的空气一样！猫猫售货机祝你情人节快乐～",
                valentineItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        List<BlessingItem> womenDayItems = new ArrayList<>();
        womenDayItems.add(new BlessingItem("minecraft:red_flower", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "妇女节",
                "03-08",
                "妇女节快乐！",
                "一朵小红花，送给了不起的你！猫猫售货机祝你妇女节快乐！",
                womenDayItems,
                NekoCurrencyRegistrar.NEKO_ID,
                3));

        List<BlessingItem> foolDayItems = new ArrayList<>();
        foolDayItems.add(new BlessingItem("minecraft:poisonous_potato", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "愚人节",
                "04-01",
                "愚人节快乐！",
                "这份礼物是真是假？嘿嘿，猫猫才不告诉你！愚人节快乐（这封邮件是真的）！",
                foolDayItems,
                NekoCurrencyRegistrar.NEKO_ID,
                3));

        List<BlessingItem> laborDayItems = new ArrayList<>();
        laborDayItems.add(new BlessingItem("minecraft:iron_pickaxe", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "劳动节",
                "05-01",
                "劳动节快乐！",
                "劳动最光荣！猫猫售货机向每一位挥动镐子的冒险者致敬，劳动节快乐！",
                laborDayItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        List<BlessingItem> childrenDayItems = new ArrayList<>();
        childrenDayItems.add(new BlessingItem("minecraft:slime_ball", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "儿童节",
                "06-01",
                "儿童节快乐！",
                "蹦蹦跳跳史莱姆！猫猫售货机祝所有大朋友小朋友儿童节快乐！",
                childrenDayItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        List<BlessingItem> teacherDayItems = new ArrayList<>();
        teacherDayItems.add(new BlessingItem("minecraft:book", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "教师节",
                "09-10",
                "教师节快乐！",
                "谢谢每一位传授知识的老师！猫猫售货机祝你教师节快乐！",
                teacherDayItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        List<BlessingItem> nationalDayItems = new ArrayList<>();
        nationalDayItems.add(new BlessingItem("minecraft:fireworks", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "国庆",
                "10-01",
                "国庆快乐！",
                "烟花贺华诞！猫猫售货机祝你国庆假期玩得开心！",
                nationalDayItems,
                NekoCurrencyRegistrar.NEKO_ID,
                8));

        List<BlessingItem> halloweenItems = new ArrayList<>();
        halloweenItems.add(new BlessingItem("minecraft:rotten_flesh", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "万圣节前夜",
                "10-31",
                "万圣夜快乐！",
                "不给糖就捣蛋！……猫猫翻遍了仓库只有这个，不要嫌弃喵！万圣夜快乐！",
                halloweenItems,
                NekoCurrencyRegistrar.NEKO_ID,
                3));

        List<BlessingItem> christmasItems = new ArrayList<>();
        christmasItems.add(new BlessingItem("minecraft:cooked_chicken", 0, 2));
        festivals.add(
            new FestivalBlessing(
                "圣诞",
                "12-25",
                "圣诞快乐！",
                "叮叮当，叮叮当！猫猫售货机祝你圣诞快乐！",
                christmasItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        // ---- 农历节日（lunar 字段自动换算，无需逐年改日期）----

        List<BlessingItem> springItems = new ArrayList<>();
        springItems.add(new BlessingItem("minecraft:cooked_beef", 0, 2));
        festivals.add(
            new FestivalBlessing(
                "春节",
                "",
                "1-1",
                "春节快乐！",
                "爆竹声中一岁除！猫猫售货机祝你新春大吉，万事如意！",
                springItems,
                NekoCurrencyRegistrar.NEKO_ID,
                8));

        List<BlessingItem> lanternItems = new ArrayList<>();
        lanternItems.add(new BlessingItem("minecraft:cookie", 0, 2));
        festivals.add(
            new FestivalBlessing(
                "元宵",
                "",
                "1-15",
                "元宵快乐！",
                "花好月圆夜，甜甜糯糯过元宵。祝元宵快乐！",
                lanternItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        List<BlessingItem> dragonBoatItems = new ArrayList<>();
        dragonBoatItems.add(new BlessingItem("minecraft:fish", 0, 2));
        festivals.add(
            new FestivalBlessing(
                "端午",
                "",
                "5-5",
                "端午安康！",
                "粽叶飘香，龙舟竞渡！猫猫售货机祝你端午安康！",
                dragonBoatItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        List<BlessingItem> qixiItems = new ArrayList<>();
        qixiItems.add(new BlessingItem("minecraft:emerald", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "七夕",
                "",
                "7-7",
                "七夕快乐！",
                "鹊桥相会夜，星光都变得温柔。猫猫售货机祝你七夕快乐！",
                qixiItems,
                NekoCurrencyRegistrar.NEKO_ID,
                8));

        List<BlessingItem> midAutumnItems = new ArrayList<>();
        midAutumnItems.add(new BlessingItem("minecraft:pumpkin_pie", 0, 2));
        festivals.add(
            new FestivalBlessing(
                "中秋",
                "",
                "8-15",
                "中秋快乐！",
                "但愿人长久，千里共婵娟。祝中秋快乐，阖家团圆！",
                midAutumnItems,
                NekoCurrencyRegistrar.NEKO_ID,
                8));

        List<BlessingItem> doubleNinthItems = new ArrayList<>();
        doubleNinthItems.add(new BlessingItem("minecraft:apple", 0, 2));
        festivals.add(
            new FestivalBlessing(
                "重阳",
                "",
                "9-9",
                "重阳安康！",
                "登高望远，秋色正好！猫猫售货机送你平安果，祝你重阳安康！",
                doubleNinthItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        List<BlessingItem> labaItems = new ArrayList<>();
        labaItems.add(new BlessingItem("minecraft:mushroom_stew", 0, 1));
        festivals.add(
            new FestivalBlessing(
                "腊八",
                "",
                "12-8",
                "腊八快乐！",
                "过了腊八就是年！喝碗热粥暖暖的，猫猫售货机祝你腊八快乐！",
                labaItems,
                NekoCurrencyRegistrar.NEKO_ID,
                5));

        List<BlessingItem> newYearsEveItems = new ArrayList<>();
        newYearsEveItems.add(new BlessingItem("minecraft:cooked_porkchop", 0, 2));
        festivals.add(
            new FestivalBlessing(
                "除夕",
                "",
                "12-L",
                "除夕快乐！",
                "辞旧岁，迎新春！年夜饭开饭啦，猫猫售货机祝你阖家团圆，年年有余！",
                newYearsEveItems,
                NekoCurrencyRegistrar.NEKO_ID,
                8));

        birthday = new BirthdayBlessing();
        birthday.items.add(new BlessingItem("minecraft:cake", 0, 1));
    }
}
