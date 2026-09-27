package com.miaokatze.gtit.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.factory.inventory.InventoryTypes;
import com.cleanroommc.modularui.utils.Platform;
import com.miaokatze.gtit.common.items.pocket.ItemNekoDimensionPocket;
import com.miaokatze.gtit.main.GTInterestingThing;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * ★R96 S11（需求 8 第一件）：<b>B 键</b>（可换绑）打开<b>穿在饰品栏里</b>的那枚口袋。
 * <p>
 * <b>★整个类都是 client-only，且只能由 {@code ClientProxy} 显式 {@link #install()} 进入</b>
 * （骨架照 {@code ReincarnationClientFx#install()} 与 {@code ClientProxy} 那三条 try/catch）：
 * {@link KeyBinding} / {@link Keyboard} / {@link ClientRegistry} 三型全是客户端类，
 * 一旦被常驻类或 {@code common} 包引用，专用服就是 {@code NoClassDefFoundError}
 * （{@code ItemNekoDimensionPocket} 的"类污染红线"同一条纪律）。本类不经任何注解自动注册 ⇒
 * 物理专用服务器不加载 {@code ClientProxy} ⇒ 本类的构造与 {@code install} 都不可达。
 * <p>
 * <b>B 键不是白送的</b>：本仓用的 Baubles fork 自带 {@code KeyHandler}，但它的默认键码是
 * <b>0（未绑定）</b>，而且开的是<b>饰品架</b>、不是口袋面板 ⇒ 想要"按键开饰品背包"必须自己开一条腿
 * （本仓此前 0 处 keybind 先例）。形状抄 MUI2 自己的 {@code ClientProxy}：
 * {@code :76-77}（{@code new KeyBinding} + {@code ClientRegistry.registerKeyBinding}，★在 preInit）
 * 与 {@code :184-193}（{@code InputEvent.KeyInputEvent} + {@code InventoryTypes.BAUBLES.visitAll}
 * → {@code openFromBaublesClient(index)}）。
 * <p>
 * <b>可换绑</b>：走 {@code ClientRegistry.registerKeyBinding} ⇒ 进 vanilla 的
 * {@code options/keybindings}（玩家改完落 {@code options.txt}）。★本类<b>不</b>自写配置文件，
 * 也不建任何第二处按键真相。
 * <p>
 * <b>不取消事件</b>：1.7.10 的 {@code InputEvent.KeyInputEvent} 无 {@code @Cancelable}
 * （实证 {@code ReincarnationClientFx} 的类 javadoc：{@code :77-80}），★所以这里只读
 * {@code isPressed()} 做事，不假装能拦截谁。
 */
@SideOnly(Side.CLIENT)
public final class PocketBaubleKeybind {

    /** vanilla 选项界面的分组与显示名（两份 lang 各一枚键；★不自写配置的键名）。 */
    static final String KEY_LANG = "key.gtit.pocket.baubles";

    static final String KEY_CATEGORY = "key.categories.gtit";

    /** ★默认键 = B（{@code Keyboard.KEY_B}，落盘值 48）；玩家可在控制设置里换绑。 */
    static final int KEY_DEFAULT = Keyboard.KEY_B;

    private static KeyBinding openPocketKey;

    private static boolean installed;

    private PocketBaubleKeybind() {}

    /**
     * 显式安装（由 {@code ClientProxy.preInit} 调用）。★幂等：装第二次直接返回，
     * 避免同一枚 {@link KeyBinding} 进两次 {@code GameSettings} 的列表（那是重复条目，不是报错）。
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        openPocketKey = new KeyBinding(KEY_LANG, KEY_DEFAULT, KEY_CATEGORY);
        ClientRegistry.registerKeyBinding(openPocketKey);
        PocketBaubleKeybind handler = new PocketBaubleKeybind();
        // ★两条总线都挂：KeyInputEvent 住在 FML 总线，但挂两遍不会双触发（同一个事件只有一条总线发），
        // 而"只挂错一条 ⇒ 按键永远没反应"是静默失效（MUI2 自己的 ClientScreenHandler 同理挂两条）。
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
        installed = true;
        GTInterestingThing.LOG.info("[pocket] 饰品背包按键已注册（默认键 {} = B，可在控制设置里换绑）", KEY_DEFAULT);
    }

    /** 测试与门禁用的读数口：默认键码（★钉 48，也钉"只有一个 KeyBinding"这条形状由门禁 grep 负责）。 */
    public static int defaultKeyCode() {
        return KEY_DEFAULT;
    }

    /** 同上：注册用的 lang 键与分组（★两份 lang 必须有这两枚，否则界面显示原始键名）。 */
    public static String keyLangKey() {
        return KEY_LANG;
    }

    public static String keyCategoryLangKey() {
        return KEY_CATEGORY;
    }

    /**
     * 按键腿。★只在客户端（{@code KeyInputEvent} 本来就只有客户端发），★不开服务端那条
     * {@code PlayerInteractEvent} 的腿，也不新建任何 C2S 包：开界面用的是 MUI2 自带的
     * {@code openFromBaublesClient(index)} —— 它按 MUI2 自己的网络通道把"要开的工厂 + 槽位"
     * 递给服务端（{@code GuiManager#openFromClient} 的注释逐字写着 "notify server to open the gui"），
     * 服务端再建 container 回推 ⇒ 面板拿到的是<b>真会话</b>，不是只有客户端画的一张皮。
     */
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openPocketKey == null || !openPocketKey.isPressed()) {
            return;
        }
        final EntityPlayer player = Platform.getClientPlayer();
        if (player == null) {
            return;
        }
        // ★饰品背包这一路只在 Baubles 在场时走：不在场时 MUI2 的 InventoryType 直接抛
        // IllegalArgumentException（PlayerInventoryGuiFactory:32-34 与 :55-57 两处都是这么写的），
        // 所以这道 isLoaded 闸是必需的，不是可选的。
        if (ModularUI.Mods.BAUBLES.isLoaded()) {
            final boolean opened = InventoryTypes.BAUBLES.visitAll(player, (type, index, stack) -> {
                if (stack != null && stack.getItem() instanceof ItemNekoDimensionPocket) {
                    GuiFactories.playerInventory()
                        .openFromBaublesClient(index);
                    return true;
                }
                return false;
            });
            if (opened) {
                return;
            }
        }
        // ★降级腿（Baubles 缺席，或口袋根本没穿上）：回既有的主手那一路。
        // 判据仍是"手上这一枚确实是口袋"——手持时既有语义（潜行右击抽液 / 非潜行右击开屏）一字未动，
        // B 键只是给不穿饰品的人第二条开屏的手，不改变任何一种右击的结果。
        final ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() instanceof ItemNekoDimensionPocket) {
            GuiFactories.playerInventory()
                .openFromMainHandClient();
        }
    }
}
