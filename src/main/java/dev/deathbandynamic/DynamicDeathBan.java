package dev.deathbandynamic;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DynamicDeathBan implements ModInitializer {
    public static final String MOD_ID = "dynamicdeathban";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public static MinecraftServer server;
    public static DynamicDeathBanConfig config;
    public static BanManager bans;

    /** Kicks are delayed a few ticks: kicking inside the join/respawn events leaves a ghost player in the world. */
    private record Pending(ServerPlayer player, String durationText, int[] delay) {}
    private static final List<Pending> PENDING = new ArrayList<>();

    @Override
    public void onInitialize() {
        config = DynamicDeathBanConfig.load();

        PayloadTypeRegistry.clientboundPlay().register(DeathbanPayload.TYPE, DeathbanPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            server = s;
            bans = BanManager.load(s);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (bans != null) bans.save();
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) onDeath(player, source);
        });

        // Banned player on the death screen clicked "Respawn" -> kick (a couple of ticks later).
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) scheduleKickIfBanned(newPlayer, null, 2);
        });
        // Banned player tries to join -> kick once the join has fully finished.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) ->
                scheduleKickIfBanned(handler.getPlayer(), null, 5));

        // Run the delayed kicks.
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (PENDING.isEmpty()) return;
            List<Pending> ready = new ArrayList<>();
            for (Pending p : PENDING) if (--p.delay()[0] <= 0) ready.add(p);
            PENDING.removeAll(ready);
            for (Pending p : ready) kickIfBanned(p.player(), p.durationText());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> DynamicDeathBanCommand.register(dispatcher));
    }

    private static void onDeath(ServerPlayer player, DamageSource source) {
        if (!config.enabled || bans == null || config.isExempt(player)) return;

        DynamicDeathBanConfig.Resolved r = config.resolve(player, source);
        long millis;
        try {
            millis = DurationUtil.parse(r.duration());
        } catch (IllegalArgumentException ex) {
            LOG.warn("Invalid duration '{}' in dynamicdeathban.json, using 1h.", r.duration());
            millis = 3_600_000L;
        }
        if (millis == 0) return;

        String durationText = millis < 0 ? "permanent" : r.duration();

        BanManager.Entry e = bans.get(player.getUUID(), player.getName().getString());
        e.expiry = millis < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + millis;
        e.cause = r.label();
        bans.save();

        boolean hasClientMod = ServerPlayNetworking.canSend(player, DeathbanPayload.TYPE);
        if (hasClientMod) ServerPlayNetworking.send(player, new DeathbanPayload(BanManager.remaining(e), false));

        if (config.announce) {
            String msg = fill(config.announceMessage, player.getName().getString(), r.label(), durationText);
            server.getPlayerList().broadcastSystemMessage(text(msg), false);
        }

        // Vanilla clients (or keepOnDeathScreen=false) are kicked right away, like the plugin did.
        if (!(config.keepOnDeathScreen && hasClientMod)) {
            PENDING.add(new Pending(player, durationText, new int[]{1}));
        }
    }

    private static void scheduleKickIfBanned(ServerPlayer player, String durationText, int delayTicks) {
        if (bans == null || bans.active(player.getUUID()) == null) return;
        PENDING.add(new Pending(player, durationText, new int[]{delayTicks}));
    }

    /** @param durationText shown as %duration%; null = time remaining right now */
    public static void kickIfBanned(ServerPlayer player, String durationText) {
        if (bans == null) return;
        BanManager.Entry e = bans.active(player.getUUID());
        if (e == null) return;
        String dur = durationText != null ? durationText : DurationUtil.format(BanManager.remaining(e));
        String msg = fill(config.banMessage, player.getName().getString(), e.cause == null ? "unknown" : e.cause, dur);
        // Tell mod clients how long is left so they can show a live countdown on the disconnect screen.
        if (ServerPlayNetworking.canSend(player, DeathbanPayload.TYPE)) {
            ServerPlayNetworking.send(player, new DeathbanPayload(BanManager.remaining(e), true));
        }
        player.connection.disconnect(text(msg));
    }

    private static String fill(String tpl, String player, String cause, String duration) {
        return tpl.replace("%player%", player).replace("%cause%", cause).replace("%duration%", duration);
    }

    public static Component text(String s) {
        return Component.literal(s.replace('&', '\u00a7'));
    }
}
