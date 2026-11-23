package dev.deathbandynamic.client;

import dev.deathbandynamic.DeathbanPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;

/**
 * Optional client half: a live "DEATHBANNED - unbanned in ..." countdown on the death screen.
 * If this file ever fails to compile, delete it (and the "client" entrypoint in fabric.mod.json);
 * the server side still works on its own.
 */
public class DynamicDeathBanClient implements ClientModInitializer {
    private static final long NONE = -1;
    private static volatile long deadline = NONE; // epoch millis; Long.MAX_VALUE = permanent

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(DeathbanPayload.TYPE, (payload, ctx) ->
                deadline = payload.remainingMillis() < 0
                        ? Long.MAX_VALUE
                        : System.currentTimeMillis() + payload.remainingMillis());

        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> deadline = NONE);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> deadline = NONE);

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof DeathScreen)) return;
            ScreenEvents.afterRender(screen).register((s, graphics, mx, my, delta) -> {
                if (deadline == NONE) return;
                Component line;
                if (deadline == Long.MAX_VALUE) {
                    line = Component.literal("\u00a7c\u00a7lDEATHBANNED \u00a7r\u00a7c- permanently");
                } else {
                    long left = deadline - System.currentTimeMillis();
                    line = left <= 0
                            ? Component.literal("\u00a7aYour deathban is over - you can respawn!")
                            : Component.literal("\u00a7c\u00a7lDEATHBANNED \u00a7r\u00a7c- unbanned in \u00a7e" + format(left));
                }
                graphics.drawCenteredString(client.font, line, s.width / 2, 115, 0xFFFFFFFF);
            });
        });
    }

    private static String format(long ms) {
        long s = (ms + 999) / 1000;
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        if (d > 0) return String.format("%dd %02dh %02dm %02ds", d, h, m, sec);
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, sec);
        return String.format("%dm %02ds", m, sec);
    }
}
