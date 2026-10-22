package com.miaokatze.gtit.gui.vm;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.miaokatze.gtit.trade.NekoTeamStatusService;
import com.miaokatze.gtit.trade.TeamStatusPage;
import com.miaokatze.gtit.trade.TeamStatusPage.Source;
import com.miaokatze.gtit.trade.TeamStatusPage.State;

/** Requests contain a single navigation action, never a player/team identifier. */
final class NekoTeamStatusSync extends SyncHandler<NekoTeamStatusSync> {

    static final int BQ = 0, NEKO = 1, PREVIOUS = 2, NEXT = 3, REFRESH = 4;
    private TeamStatusPage view = TeamStatusPage.empty(Source.BQ, State.LOADING);
    // Netty writes only one bounded slot; authoritative reads run from the server's container tick.
    private final AtomicInteger pending = new AtomicInteger(-1);
    private int ticks;
    private int lastQuery = -100;

    NekoTeamStatusSync() {
        allowC2S();
    }

    TeamStatusPage view() {
        return view;
    }

    void request(int action) {
        syncToServer(action);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buffer) throws IOException {
        if (id < BQ || id > REFRESH || buffer.isReadable()) throw new IOException("Invalid team view request");
        pending.set(id);
    }

    @Override
    public void readOnClient(int id, PacketBuffer buffer) throws IOException {
        if (id != 0) throw new IOException("Invalid team view response");
        view = TeamStatusPage.read(buffer);
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        ticks++;
        if (!init && (ticks - lastQuery < 5 || (pending.get() < 0 && ticks - lastQuery < 100))) return;
        int action = pending.getAndSet(-1);
        Source source = action == BQ ? Source.BQ : action == NEKO ? Source.NEKO : view.source;
        int page = source == view.source ? view.page : 0;
        if (action == PREVIOUS) page = Math.max(0, page - 1);
        if (action == NEXT) page = Math.min(TeamStatusPage.lastPage(view.total), page + 1);
        lastQuery = ticks;
        if (getSyncManager().getPlayer() instanceof EntityPlayerMP player) {
            view = NekoTeamStatusService.query(player, source, page);
        } else {
            view = TeamStatusPage.empty(source, State.ERROR);
        }
        syncToClient(0, view::write);
    }
}
