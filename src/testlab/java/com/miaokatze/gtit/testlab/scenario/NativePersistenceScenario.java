package com.miaokatze.gtit.testlab.scenario;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;

import com.miaokatze.gtit.Tags;
import com.miaokatze.gtit.common.api.enums.GTITItemList;
import com.miaokatze.gtit.common.items.pocket.PocketInventory;
import com.miaokatze.gtit.common.items.pocket.PocketUpgradeType;
import com.miaokatze.gtit.common.items.pocket.PocketUpgrades;
import com.miaokatze.gtit.currency.NekoCurrencyRegistrar;
import com.miaokatze.gtit.mail.Mail;
import com.miaokatze.gtit.mail.MailData;
import com.miaokatze.gtit.signin.DailySignInData;
import com.miaokatze.gtit.testlab.ProbeContext;
import com.miaokatze.gtit.testlab.ProbeScenario;
import com.miaokatze.gtit.testlab.ProbeTask;
import com.miaokatze.gtit.trade.NekoWallet;

/** Explicit two-phase codec test. Its only durable fixture is a checksummed testlab scratch file. */
final class NativePersistenceScenario implements ProbeScenario {

    private static final String JVM = ManagementFactory.getRuntimeMXBean()
        .getName() + ":"
        + ManagementFactory.getRuntimeMXBean()
            .getStartTime();
    private static final File FILE = new File("logs/gtit-testlab/persistence/native-roundtrip.dat");
    private static final long FLUID_AMOUNT = 1_500_000_123L;
    private final boolean write;

    NativePersistenceScenario(boolean write) {
        this.write = write;
    }

    @Override
    public String id() {
        return write ? "native-persistence-write" : "native-persistence-read";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public ProbeTask start(ProbeContext context) {
        return () -> {
            if (write) write(context);
            else read(context);
            return true;
        };
    }

    private static void write(ProbeContext context) throws Exception {
        if (FILE.exists() || new File(FILE + "_tmp").exists()) {
            context.check(
                "scratch-unused",
                false,
                "Pending phase file exists; run native-persistence-read in a new JVM before another write");
            return;
        }
        Files.createDirectories(
            FILE.toPath()
                .getParent());
        NBTTagCompound payload = new NBTTagCompound();
        ItemStack carrier = context.mark(GTITItemList.NekoDimensionPocket.get(1));
        PocketUpgrades.install(carrier, PocketUpgradeType.CAPACITY);
        PocketInventory inventory = PocketInventory.readFrom(carrier.getTagCompound());
        inventory.depositIntoStorage(NativeFixtures.specialStack(context, 17));
        inventory.fillOwnTankL(0, FluidRegistry.WATER, FLUID_AMOUNT);
        inventory.writeTo(carrier.getTagCompound());
        payload.setTag("pocket", carrier.writeToNBT(new NBTTagCompound()));
        NekoWallet wallet = new NekoWallet();
        wallet.addCount(NekoCurrencyRegistrar.NEKO_ID, 42);
        wallet.addCount(NekoCurrencyRegistrar.SHIMMERING_NEKO_ID, 19);
        payload.setTag("wallet", wallet.writeToNBT());
        DailySignInData signIn = new DailySignInData();
        signIn.recordSignIn("2026-01-02", "2026-01-01", 1);
        signIn.claimTierReward(1, "2026-01");
        payload.setTag("signin", signIn.writeToNBT());
        MailData mailData = new MailData();
        Mail mail = new Mail(
            "跨 JVM",
            "testlab scratch",
            "GTIT",
            Collections.singletonList(NativeFixtures.specialStack(context, 5)));
        mailData.addMail(mail);
        mailData.markReceivedOnce("testlab-once");
        payload.setTag("mail", mailData.writeToNBT());
        payload.setString("mailId", mail.getId());
        byte[] bytes = CompressedStreamTools.compress(payload);
        NBTTagCompound envelope = new NBTTagCompound();
        envelope.setInteger("schema", 1);
        envelope.setString("producer", "gtit-testlab-native-codec");
        envelope.setString("version", Tags.VERSION);
        envelope.setString("writerJvm", JVM);
        envelope.setString("runId", context.runId());
        envelope.setString(
            "world",
            context.world()
                .getSaveHandler()
                .getWorldDirectory()
                .getCanonicalPath());
        envelope.setInteger("dimension", context.world().provider.dimensionId);
        envelope.setByteArray("payload", bytes);
        envelope.setByteArray(
            "sha256",
            MessageDigest.getInstance("SHA-256")
                .digest(bytes));
        CompressedStreamTools.safeWrite(envelope, FILE);
        NBTTagCompound reread = CompressedStreamTools.read(FILE);
        context.check(
            "native-codec-scratch-written",
            FILE.isFile() && reread != null && Arrays.equals(bytes, reread.getByteArray("payload")),
            "Native pocket/wallet/sign-in/mail codecs written to " + FILE + "; writer=" + JVM);
        context.metric("scratchPayloadBytes", bytes.length);
        context.skip(
            "cross-jvm-read-pending",
            "Restart this server JVM, then explicitly run native-persistence-read; scratch intentionally retained");
    }

    private static void read(ProbeContext context) throws Exception {
        if (!FILE.isFile()) {
            context.check(
                "cross-jvm-source-exists",
                false,
                "Run native-persistence-write, then restart the JVM before reading");
            return;
        }
        NBTTagCompound envelope = CompressedStreamTools.read(FILE);
        boolean source = envelope != null && envelope.getInteger("schema") == 1
            && "gtit-testlab-native-codec".equals(envelope.getString("producer"))
            && context.world()
                .getSaveHandler()
                .getWorldDirectory()
                .getCanonicalPath()
                .equals(envelope.getString("world"))
            && context.world().provider.dimensionId == envelope.getInteger("dimension")
            && !envelope.getString("runId")
                .isEmpty();
        context.check(
            "cross-jvm-source",
            source,
            "Require testlab producer/schema, source world/dimension and writer run ID");
        if (!source) return;
        boolean newJvm = !JVM.equals(envelope.getString("writerJvm"));
        context.check(
            "different-jvm-required",
            newJvm,
            "writer=" + envelope
                .getString("writerJvm") + "; reader=" + JVM + "; producerVersion=" + envelope.getString("version"));
        if (!newJvm) return;
        byte[] bytes = envelope.getByteArray("payload");
        boolean checksum = bytes.length > 0 && bytes.length <= 1_048_576
            && Arrays.equals(
                MessageDigest.getInstance("SHA-256")
                    .digest(bytes),
                envelope.getByteArray("sha256"));
        context.check("cross-jvm-sha256", checksum, "Compressed payload checksum validated before native codec reads");
        if (!checksum) return;
        NBTTagCompound payload = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes));
        ItemStack carrier = ItemStack.loadItemStackFromNBT(payload.getCompoundTag("pocket"));
        PocketInventory inventory = PocketInventory.readFrom(carrier == null ? null : carrier.getTagCompound());
        ItemStack stack = inventory.storageStack(0);
        boolean pocket = carrier != null && GTITItemList.NekoDimensionPocket.get(1)
            .isItemEqual(carrier)
            && stack != null
            && stack.stackSize == 17
            && stack.hasTagCompound()
            && "猫猫\nNBT \"quoted\" \\path".equals(
                stack.getTagCompound()
                    .getCompoundTag("nativeProbePayload")
                    .getString("unicode"))
            && stack.getTagCompound()
                .getCompoundTag("nativeProbePayload")
                .getLong("counter") == 9_007_199_254_740_991L
            && envelope.getString("runId")
                .equals(
                    stack.getTagCompound()
                        .getCompoundTag(ProbeContext.TEST_ITEM_TAG)
                        .getString("runId"))
            && inventory.tankTruthAt(0) == FLUID_AMOUNT
            && inventory.ownTankFluid(0)
                .getFluid() == FluidRegistry.WATER;
        context.check(
            "cross-jvm-pocket-item-fluid",
            pocket,
            "Registered pocket, nested special NBT, original test marker and long water quantity survive a JVM restart");
        NekoWallet wallet = new NekoWallet();
        wallet.readFromNBT(payload.getCompoundTag("wallet"));
        boolean balance = wallet.getCount(NekoCurrencyRegistrar.NEKO_ID) == 42
            && wallet.getCount(NekoCurrencyRegistrar.SHIMMERING_NEKO_ID) == 19;
        context.check(
            "cross-jvm-wallet-codec",
            balance,
            "Both native wallet currencies survive restart through their production NBT codec");
        DailySignInData signIn = new DailySignInData();
        signIn.readFromNBT(payload.getCompoundTag("signin"));
        boolean signed = signIn.getTotalSignInDays() == 1 && signIn.hasSignedToday("2026-01-02")
            && signIn.hasClaimedTier(1, "2026-01");
        context.check(
            "cross-jvm-signin-codec",
            signed,
            "Native sign-in date, total and anti-repeat tier key survive restart");
        MailData mailData = new MailData();
        mailData.readFromNBT(payload.getCompoundTag("mail"));
        Mail mail = mailData.findMail(payload.getString("mailId"));
        boolean mailed = mail != null && mail.hasUnclaimedAttachments()
            && mailData.hasReceivedOnce("testlab-once")
            && mail.getAttachments()
                .size() == 1
            && mail.getAttachments()
                .get(0).stackSize == 5
            && mail.getAttachments()
                .get(0)
                .getTagCompound()
                .getCompoundTag("nativeProbePayload")
                .getLong("counter") == 9_007_199_254_740_991L;
        context.check(
            "cross-jvm-mail-codec",
            mailed,
            "Native mail identity, special attachment and anti-repeat reward marker survive restart");
        if (pocket && balance && signed && mailed) {
            Files.delete(FILE.toPath());
            context.check(
                "cross-jvm-scratch-consumed",
                !FILE.exists(),
                "Successful read consumes only this testlab scratch file");
        }
        context.skip(
            "production-save-restart",
            "This proves production model codecs across JVMs using testlab scratch; native manager save/reload is tested separately by native-business-events");
    }
}
