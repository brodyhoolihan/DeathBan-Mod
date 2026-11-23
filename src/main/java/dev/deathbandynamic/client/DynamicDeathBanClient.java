package dev.deathbandynamic.client;

import dev.deathbandynamic.DeathbanPayload;
import dev.deathbandynamic.KickPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;

/**
 * Optional client half: a live "DEATHBANNED - unbanned in ..." countdown on the death screen
 * and on the "Disconnected" screen you get when the server kicks you for being banned.
 * If this file ever fails to compile, delete it (and the "client" entrypoint in fabric.mod.json);
 * the server side still works on its own.
 */
public class DynamicDeathBanClient implements ClientModInitializer {
    private static final long NONE = -1;
    private static volatile long deadline = NONE;      // used on the death screen
    private static volatile long kickDeadline = NONE;  // used on the disconnect screen

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(DeathbanPayload.TYPE, (payload, ctx) ->
                deadline = toDeadline(payload.remainingMillis()));
        ClientPlayNetworking.registerGlobalReceiver(KickPayload.TYPE, (payload, ctx) ->
                kickDeadline = toDeadline(payload.remainingMillis()));

        // A new join clears old state; the server's ban packet (if any) arrives after this.
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> {
            deadline = NONE;
            kickDeadline = NONE;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> deadline = NONE);

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof DeathScreen) {
                ScreenEvents.afterExtract(screen).register((s, graphics, mx, my, delta) -> {
                    if (deadline == NONE) return;
                    Component line = line(deadline);
                    graphics.text(client.font, line, s.width / 2 - client.font.width(line) / 2, 115, 0xFFFFFFFF, true);
                });
            } else if (screen instanceof DisconnectedScreen) {
                ScreenEvents.afterExtract(screen).register((s, graphics, mx, my, delta) -> {
                    if (kickDeadline == NONE) return;
                    Component line = line(kickDeadline);
                    graphics.text(client.font, line, s.width / 2 - client.font.width(line) / 2, 20, 0xFFFFFFFF, true);
                });
                // Leaving the disconnect screen forgets the countdown.
                ScreenEvents.remove(screen).register(s -> kickDeadline = NONE);
            }
        });
    }

    private static long toDeadline(long remainingMillis) {
        return remainingMillis < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + remainingMillis;
    }

    private static Component line(long dl) {
        if (dl == Long.MAX_VALUE) {
            return Component.literal("\u00a7c\u00a7lDEATHBANNED \u00a7r\u00a7c- permanently");
        }
        long left = dl - System.currentTimeMillis();
        if (left <= 0) return Component.literal("\u00a7aYour deathban is over - you can rejoin!");
        return Component.literal("\u00a7c\u00a7lDEATHBANNED \u00a7r\u00a7c- unbanned in \u00a7e" + format(left));
    }

    private static String format(long ms) {
        long s = (ms + 999) / 1000;
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        if (d > 0) return String.format("%dd %02dh %02dm %02ds", d, h, m, sec);
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, sec);
        return String.format("%dm %02ds", m, sec);
    }
}
