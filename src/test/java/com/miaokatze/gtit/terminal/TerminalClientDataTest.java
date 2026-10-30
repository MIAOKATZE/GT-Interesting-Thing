package com.miaokatze.gtit.terminal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

/** World changes must clear cached administrative data; snapshots must remain detached and immutable. */
public final class TerminalClientDataTest {

    public static void main(String[] args) {
        List<String> players = new ArrayList<>(Arrays.asList("previous-world"));
        TerminalClientData.setOnlinePlayers(players);
        players.clear();
        check(
            TerminalClientData.getOnlinePlayers()
                .size() == 1,
            "player snapshot is detached");
        try {
            TerminalClientData.getOnlinePlayers()
                .clear();
            throw new AssertionError("player snapshot must be immutable");
        } catch (UnsupportedOperationException expected) {}
        TerminalClientData.setLastResult(7, 0, "old action");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", "old template");
        tag.setString("body", "old body");
        tag.setBoolean("hasAttachment", true);
        TerminalClientData.applyData(TerminalClientData.DATA_TYPE_MAIL_TEMPLATE, tag);
        tag.setString("lines", "old player\nold result");
        tag.setInteger("total", 3);
        TerminalClientData.applyData(TerminalClientData.DATA_TYPE_SIGNIN_SUMMARY, tag);
        TerminalClientData.applyData(TerminalClientData.DATA_TYPE_GIFT_LIST, tag);
        TerminalClientData.resetSession();
        check(
            TerminalClientData.getOnlinePlayers()
                .isEmpty(),
            "old online players cleared");
        check(
            TerminalClientData.getLastAction() == -1 && TerminalClientData.getLastStatus() == -1,
            "old action/status cleared");
        check(
            TerminalClientData.getLastResultMessage()
                .isEmpty() && TerminalClientData.getLastResultAt() == 0,
            "old result cleared");
        check(
            TerminalClientData.getMailTemplateTitle()
                .isEmpty()
                && TerminalClientData.getMailTemplateBody()
                    .isEmpty()
                && !TerminalClientData.isMailTemplateHasAttachment(),
            "old template cleared");
        check(
            TerminalClientData.getSignInSummaryLines()
                .isEmpty(),
            "old sign-in summary cleared");
        check(
            TerminalClientData.getGiftListLines()
                .isEmpty() && TerminalClientData.getGiftTotalCount() == 0,
            "old gift query cleared");
        TerminalClientData.resetSession();
        TerminalClientData.setOnlinePlayers(Arrays.asList("next-world"));
        check(
            TerminalClientData.getOnlinePlayers()
                .equals(Arrays.asList("next-world")),
            "next session still updates");
        System.out.println("TerminalClientDataTest: detached snapshot, immutable list and complete world reset passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
