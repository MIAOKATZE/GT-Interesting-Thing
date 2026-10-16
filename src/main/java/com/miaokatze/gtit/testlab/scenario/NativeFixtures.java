package com.miaokatze.gtit.testlab.scenario;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.gtnewhorizon.gtnhlib.teams.TeamManager;
import com.miaokatze.gtit.lottery.LotteryManager;
import com.miaokatze.gtit.mail.MailManager;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.trade.v2.NekoHistoryManager;

final class NativeFixtures {

    private NativeFixtures() {}

    static ItemStack specialStack(ProbeContext context, int count) {
        ItemStack stack = context.mark(new ItemStack(Items.paper, count));
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("unicode", "猫猫\nNBT \"quoted\" \\path");
        payload.setLong("counter", 9_007_199_254_740_991L);
        payload.setByteArray("bytes", new byte[] { 0, -1, 17, 127 });
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("alpha"));
        list.appendTag(new NBTTagString("βeta"));
        payload.setTag("list", list);
        stack.getTagCompound()
            .setTag("nativeProbePayload", payload);
        return stack;
    }

    static boolean sameStack(ItemStack expected, ItemStack actual) {
        return expected != null && actual != null
            && expected.stackSize == actual.stackSize
            && expected.isItemEqual(actual)
            && ItemStack.areItemStackTagsEqual(expected, actual);
    }

    /** Resolve every cleanup field before creating fixtures; a changed implementation fails before mutation. */
    static void ownIdentity(ProbeContext context, UUID id) throws ReflectiveOperationException {
        List<Runnable> undo = new ArrayList<>();
        List<Object> tracked = new ArrayList<>();
        List<File> files = new ArrayList<>();
        if (TeamManager.getTeamMap()
            .values()
            .stream()
            .anyMatch(team -> team.isMember(id)))
            throw new IllegalStateException("Synthetic UUID already belongs to a team: " + id);
        Object[] managers = { NekoWalletManager.INSTANCE, DailySignInManager.INSTANCE, MailManager.INSTANCE,
            LotteryManager.INSTANCE, NekoHistoryManager.INSTANCE };
        String[][] keys = { { "personalWallets", "dirtyPersonalWallets", "dirtyBalanceKeys" }, { "signInDataMap" },
            { "mailDataMap" }, { "pityCounters" }, { "histories", "legacyHistoryConsumed" } };
        for (int i = 0; i < managers.length; i++) {
            Object manager = managers[i];
            File directory = (File) field(manager, "saveDir");
            for (String key : keys[i]) {
                Object collection = field(manager, key);
                tracked.add(collection);
                if (collection instanceof Map && ((Map<?, ?>) collection).containsKey(id)
                    || collection instanceof Collection && (((Collection<?>) collection).contains(id)
                        || ((Collection<?>) collection).contains("player:" + id)
                        || ((Collection<?>) collection).contains("team:" + id))) {
                    throw new IllegalStateException("Synthetic identity already cached: " + id);
                }
                undo.add(() -> {
                    if (collection instanceof Map) ((Map<?, ?>) collection).remove(id);
                    if (collection instanceof Collection) {
                        Collection<?> values = (Collection<?>) collection;
                        values.remove(id);
                        values.remove("player:" + id);
                        values.remove("team:" + id);
                    }
                });
            }
            if (directory != null) {
                File file = new File(directory, id + ".dat");
                files.add(file);
                files.add(new File(file + "_tmp"));
                if (file.exists() || new File(file + "_tmp").exists()) {
                    throw new IllegalStateException("Synthetic identity already has data: " + id);
                }
                undo.add(() -> {
                    try {
                        Files.deleteIfExists(file.toPath());
                        Files.deleteIfExists(new File(file + "_tmp").toPath());
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException("Cannot remove synthetic fixture file " + file, error);
                    }
                });
            }
        }
        context.onCleanup(() -> {
            boolean absent = true;
            for (Object collection : tracked) {
                if (collection instanceof Map) absent &= !((Map<?, ?>) collection).containsKey(id);
                if (collection instanceof Collection) {
                    Collection<?> values = (Collection<?>) collection;
                    absent &= !values.contains(id) && !values.contains("player:" + id)
                        && !values.contains("team:" + id);
                }
            }
            for (File file : files) absent &= !file.exists();
            context.check(
                "cleanup-identity-" + id.toString()
                    .substring(0, 8),
                absent,
                "Exact synthetic UUID absent from " + tracked.size()
                    + " manager caches/dirty sets and "
                    + files.size()
                    + " native data/tmp paths");
        });
        for (Runnable action : undo) context.onCleanup(action);
    }

    static Object field(Object owner, String name) throws ReflectiveOperationException {
        Class<?> type = owner instanceof Class ? (Class<?>) owner : owner.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner instanceof Class ? null : owner);
    }

    static boolean cached(Object manager, String name, UUID id) throws ReflectiveOperationException {
        return ((Map<?, ?>) field(manager, name)).containsKey(id);
    }

    static File dataFile(Object manager, UUID id) throws ReflectiveOperationException {
        File directory = (File) field(manager, "saveDir");
        return directory == null ? null : new File(directory, id + ".dat");
    }
}
