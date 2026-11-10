package com.miaokatze.gtit.testlab.scenario;

import java.io.File;
import java.util.Collections;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtit.common.items.pocket.PocketChannelManager;
import com.miaokatze.gtit.common.items.pocket.PocketSessions;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.lottery.LotteryHandler;
import com.miaokatze.gtit.lottery.LotteryManager;
import com.miaokatze.gtit.mail.Mail;
import com.miaokatze.gtit.mail.MailData;
import com.miaokatze.gtit.mail.MailManager;
import com.miaokatze.gtit.signin.DailySignInData;
import com.miaokatze.gtit.signin.DailySignInManager;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.gtit.trade.NekoWallet;
import com.miaokatze.gtit.trade.NekoWalletManager;
import com.miaokatze.gtit.util.ServerTaskScheduler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.gameevent.PlayerEvent;

final class BusinessLifecycleScenario implements ProbeScenario {

    @Override
    public String id() {
        return "native-business-events";
    }

    @Override
    public ProbeTask start(ProbeContext context) throws Exception {
        FakePlayer player = context.player("lifecycle");
        UUID id = player.getUniqueID();
        NativeFixtures.ownIdentity(context, id);
        context.onCleanup(() -> PocketChannelManager.INSTANCE.forget(id));
        boolean[] active = { true };
        context.onCleanup(() -> active[0] = false);
        return new ProbeTask() {

            private int tick;
            private int immediate;
            private int delayed;

            @Override
            public boolean tick() throws Exception {
                if (tick++ == 0) {
                    exerciseBusiness(context, player);
                    ServerTaskScheduler.scheduleServerTask(() -> { if (active[0]) immediate++; });
                    LotteryHandler.scheduleDelayedTask(0, () -> { if (active[0]) delayed++; });
                    context.check(
                        "queued-tasks-not-inline",
                        immediate == 0 && delayed == 0,
                        "Actual shared task queues defer execution to their registered server END handlers");
                    return false;
                }
                if (tick < 4) return false;
                context.check(
                    "natural-end-task-dispatch",
                    immediate == 1 && delayed == 1,
                    "Natural server events execute shared immediate and lottery delayed callbacks once; immediate="
                        + immediate
                        + ", delayed="
                        + delayed);
                context.skip(
                    "login-and-reward-events",
                    "Login/reward delivery, respawn, dimension transfer, interaction and client events are outside this probe");
                context.skip(
                    "lottery-reward-dispatch",
                    "No global pool/config mutation; actual reward animation and random draw delivery require separate fixtures");
                return true;
            }
        };
    }

    private static void exerciseBusiness(ProbeContext context, FakePlayer player) throws Exception {
        UUID id = player.getUniqueID();
        String coin = NekoCurrencyRegistrar.NEKO_ID;
        NekoWallet wallet = NekoWalletManager.INSTANCE.getWallet(id);
        wallet.addCount(coin, 27);
        DailySignInData signIn = DailySignInManager.INSTANCE.getSignInData(id);
        String today = DailySignInManager.getToday();
        signIn.recordSignIn(today, DailySignInManager.getYesterday(), 1);
        int days = signIn.getTotalSignInDays();
        context.check(
            "duplicate-signin-refused",
            DailySignInManager.INSTANCE.signIn(id)
                .getStatus() == DailySignInManager.SignInResult.Status.ALREADY_SIGNED
                && signIn.getTotalSignInDays() == days
                && wallet.getCount(coin) == 27,
            "Actual manager duplicate branch leaves total days and wallet unchanged");
        ItemStack attachment = NativeFixtures.specialStack(context, 5);
        Mail mail = new Mail(
            "GTIT testlab",
            "Native mailbox fixture",
            "testlab",
            Collections.singletonList(attachment));
        MailManager mailManager = MailManager.INSTANCE;
        context.check(
            "native-mail-send",
            mailManager.sendMail(id, mail),
            "Real MailManager sends only to an offline synthetic UUID");
        context.check(
            "unclaimed-mail-delete-refused",
            mailManager.deleteMail(id, mail.getId()) == 2,
            "Unclaimed attachment prevents destructive delete");
        context.check(
            "mail-read-idempotent",
            mailManager.markRead(id, mail.getId()) && !mailManager.markRead(id, mail.getId()),
            "First markRead changes state; the second is a no-op");
        int firstClaim = mailManager.claimAttachments(id, mail.getId(), player);
        int secondClaim = mailManager.claimAttachments(id, mail.getId(), player);
        context.check(
            "mail-claim-once",
            firstClaim == 1 && secondClaim == -1
                && NativeFixtures.sameStack(attachment, player.inventory.getStackInSlot(0)),
            "Actual manager delivers exact special NBT once into fake player's empty inventory");
        context.check(
            "claimed-mail-deletable",
            mailManager.deleteMail(id, mail.getId()) == 0 && mailManager.deleteMail(id, mail.getId()) == 1,
            "Claimed mail deletes once and then reports missing");
        Mail retained = new Mail("persist", "logout probe", "testlab", Collections.emptyList());
        mailManager.sendMail(id, retained);
        context.check(
            "lottery-invalid-count-no-side-effects",
            LotteryManager.INSTANCE.drawLottery(id, "unused", 0, null)
                .isEmpty() && wallet.getCount(coin) == 27
                && !NativeFixtures.cached(LotteryManager.INSTANCE, "pityCounters", id),
            "Actual lottery request rejects zero draws before costs, pity or delayed reward dispatch");
        // Pocket logout includes an orphan sweep. Avoid invoking it while unrelated sessions exist.
        if (PocketSessions.liveSessions() != 0) {
            context.skip(
                "fml-logout-persistence",
                "Unrelated pocket sessions exist; refusing to trigger their orphan sweep");
            return;
        }
        PocketChannelManager.INSTANCE.stateOf(id);
        mailManager.getMailData(id); // Offline send deliberately evicts its cache; load the retained mail for logout.
        context.check(
            "lifecycle-fixtures-loaded",
            NativeFixtures.cached(NekoWalletManager.INSTANCE, "personalWallets", id)
                && NativeFixtures.cached(DailySignInManager.INSTANCE, "signInDataMap", id)
                && NativeFixtures.cached(mailManager, "mailDataMap", id)
                && PocketChannelManager.INSTANCE.peek(id) != null,
            "All tested caches contain this synthetic identity before logout");
        FMLCommonHandler.instance()
            .bus()
            .post(new PlayerEvent.PlayerLoggedOutEvent(player));
        context.check(
            "fml-logout-cache-lifecycle",
            NativeFixtures.cached(NekoWalletManager.INSTANCE, "personalWallets", id)
                && !NativeFixtures.cached(DailySignInManager.INSTANCE, "signInDataMap", id)
                && !NativeFixtures.cached(mailManager, "mailDataMap", id)
                && PocketChannelManager.INSTANCE.peek(id) == null,
            "Real logout handlers retain the canonical wallet for late refunds and unload sign-in/mail/channel fixtures");
        File walletFile = NativeFixtures.dataFile(NekoWalletManager.INSTANCE, id);
        File signInFile = NativeFixtures.dataFile(DailySignInManager.INSTANCE, id);
        File mailFile = NativeFixtures.dataFile(mailManager, id);
        context.check(
            "fml-logout-native-files",
            walletFile != null && walletFile.isFile()
                && signInFile != null
                && signInFile.isFile()
                && mailFile != null
                && mailFile.isFile(),
            "Logout persisted all three test UUID records through native managers");
        NekoWallet reloadedWallet = NekoWalletManager.INSTANCE.getWallet(id);
        DailySignInData reloadedSignIn = DailySignInManager.INSTANCE.getSignInData(id);
        MailData reloadedMail = mailManager.getMailData(id);
        context.check(
            "fml-logout-native-reload",
            reloadedWallet == wallet && reloadedWallet.getCount(coin) == 27
                && reloadedSignIn != signIn
                && reloadedSignIn.hasSignedToday(today)
                && reloadedSignIn.getTotalSignInDays() == days
                && reloadedMail.findMail(retained.getId()) != null,
            "Wallet reference survives logout; fresh sign-in/mail objects reload their saved state");
    }
}
