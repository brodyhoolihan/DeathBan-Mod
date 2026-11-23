package dev.deathbandynamic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DynamicDeathBanCommand {
    private DynamicDeathBanCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("ddb")
                // NOTE: the permission API changed in 1.21.9+. If this line fails to compile, tell me the error.
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reload").executes(c -> {
                    DynamicDeathBan.config = DynamicDeathBanConfig.load();
                    reply(c.getSource(), "DynamicDeathBan config reloaded.");
                    return 1;
                }))
                .then(Commands.literal("check").then(player().executes(c -> {
                    var e = DynamicDeathBan.bans.findByName(StringArgumentType.getString(c, "player"));
                    if (e == null || e.expiry <= System.currentTimeMillis()) {
                        reply(c.getSource(), "Not deathbanned.");
                    } else {
                        reply(c.getSource(), e.name + ": " + DurationUtil.format(BanManager.remaining(e))
                                + " left (" + e.cause + ")");
                    }
                    return 1;
                })))
                .then(Commands.literal("pardon").then(player().executes(c -> {
                    var e = DynamicDeathBan.bans.findByName(StringArgumentType.getString(c, "player"));
                    if (e == null) { reply(c.getSource(), "Unknown player."); return 0; }
                    e.expiry = 0;
                    DynamicDeathBan.bans.save();
                    reply(c.getSource(), "Pardoned " + e.name + ".");
                    return 1;
                })))
                .then(Commands.literal("set").then(player()
                        .then(Commands.argument("duration", StringArgumentType.word()).executes(c -> {
                            String name = StringArgumentType.getString(c, "player");
                            long ms;
                            try {
                                ms = DurationUtil.parse(StringArgumentType.getString(c, "duration"));
                            } catch (IllegalArgumentException ex) {
                                reply(c.getSource(), ex.getMessage());
                                return 0;
                            }
                            var e = DynamicDeathBan.bans.findByName(name);
                            if (e == null) {
                                reply(c.getSource(), "No record for that player (they must have died once).");
                                return 0;
                            }
                            e.expiry = ms == 0 ? 0 : ms < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + ms;
                            if (e.cause == null) e.cause = "an admin";
                            DynamicDeathBan.bans.save();
                            for (ServerPlayer p : DynamicDeathBan.server.getPlayerList().getPlayers()) {
                                if (p.getName().getString().equalsIgnoreCase(name)) DynamicDeathBan.kickIfBanned(p, null);
                            }
                            reply(c.getSource(), "Set " + e.name + " to " + DurationUtil.format(BanManager.remaining(e)) + ".");
                            return 1;
                        })))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> player() {
        return Commands.argument("player", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(DynamicDeathBan.bans.names(), b));
    }

    private static void reply(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), true);
    }
}
