package dev.deathbandynamic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** config/dynamicdeathban.json - same cause names and default durations as the original plugin's config.yml. */
public class DynamicDeathBanConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dynamicdeathban.json");

    public boolean enabled = true;

    /** Ban screen / kick message. Supports & color codes. Placeholders: %cause% %duration% %player% */
    public String banMessage = "&cYou died (%cause%).\n&7You are banned for &f%duration%&7.";

    /** Broadcast to everyone when a deathban starts. Set announce=false to disable. */
    public boolean announce = true;
    public String announceMessage = "&c\u2620 &f%player% &cdied (%cause%) and is deathbanned for &e%duration%&c.";

    /**
     * true  = players who have the mod stay on the death screen (live countdown) and are kicked when they
     *         click Respawn. Players without the mod are always kicked immediately, like the plugin did.
     * false = everyone is kicked immediately.
     */
    public boolean keepOnDeathScreen = true;

    /** Player names or UUIDs that are never deathbanned (replaces the dynamicdeathban.bypass permission). */
    public List<String> exemptPlayers = new ArrayList<>();

    /** Durations: number + unit s, m, h, d, w (combinable: 1d12h). "perm" = permanent, "0" = no ban. */
    public LinkedHashMap<String, String> durations = defaultDurations();

    /**
     * Which damage types belong to which cause. Entries are damage type ids, or "#tag" for a damage type tag.
     * PLAYER_KILL, ENTITY_EXPLOSION and BLOCK_EXPLOSION are decided in code (see README); anything not
     * listed here uses DEFAULT.
     */
    public LinkedHashMap<String, List<String>> causes = defaultCauses();

    /**
     * Optional extra rules checked BEFORE the causes above, for very specific deaths
     * (e.g. creeper kills in the Nether). First match wins. Empty by default.
     */
    public List<Rule> customRules = new ArrayList<>();

    public static class Rule {
        public String name = "custom";
        public List<String> damageTypes;   // any of
        public String damageTag;
        public String killerEntity;        // e.g. "minecraft:creeper"
        public String directEntity;        // e.g. "minecraft:arrow"
        public String dimension;           // e.g. "minecraft:the_nether"
        public String duration = "1h";

        boolean matches(ServerPlayer p, DamageSource src) {
            if (damageTypes != null && !damageTypes.contains(src.typeHolder().getRegisteredName())) return false;
            if (damageTag != null && !src.is(tag(damageTag))) return false;
            if (killerEntity != null && !entityIs(src.getEntity(), killerEntity)) return false;
            if (directEntity != null && !entityIs(src.getDirectEntity(), directEntity)) return false;
            if (dimension != null && !dimension.equals(p.level().dimension().identifier().toString())) return false;
            return true;
        }
    }

    public record Resolved(String key, String label, String duration) {}

    // ------------------------------------------------------------------ resolution

    public Resolved resolve(ServerPlayer p, DamageSource src) {
        for (Rule r : customRules) {
            if (r.matches(p, src)) return new Resolved("CUSTOM", r.name, r.duration);
        }
        String key = causeKey(p, src);
        String duration = durations.getOrDefault(key, durations.getOrDefault("DEFAULT", "1h"));
        String label = key.equals("DEFAULT")
                ? idPath(src.typeHolder().getRegisteredName()).replace('_', ' ')
                : key.toLowerCase().replace('_', ' ');
        return new Resolved(key, label, duration);
    }

    private String causeKey(ServerPlayer p, DamageSource src) {
        // Same as Bukkit's getKiller(): the last player who hurt you recently, or a direct player attacker.
        if ((src.getEntity() instanceof Player && src.getEntity() != p) || p.getLastHurtByPlayer() != null) {
            return "PLAYER_KILL";
        }
        if (src.is(DamageTypeTags.IS_EXPLOSION)) {
            return (src.getEntity() != null || src.getDirectEntity() != null) ? "ENTITY_EXPLOSION" : "BLOCK_EXPLOSION";
        }
        String id = src.typeHolder().getRegisteredName();
        for (Map.Entry<String, List<String>> en : causes.entrySet()) {
            for (String m : en.getValue()) {
                if (m.startsWith("#") ? src.is(tag(m)) : m.equals(id)) return en.getKey();
            }
        }
        return "DEFAULT";
    }

    public boolean isExempt(ServerPlayer p) {
        String name = p.getName().getString();
        String uuid = p.getUUID().toString();
        for (String s : exemptPlayers) if (s.equalsIgnoreCase(name) || s.equalsIgnoreCase(uuid)) return true;
        return false;
    }

    private static TagKey<DamageType> tag(String s) {
        return TagKey.create(Registries.DAMAGE_TYPE, Identifier.parse(s.startsWith("#") ? s.substring(1) : s));
    }

    private static boolean entityIs(Entity e, String id) {
        return e != null && id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
    }

    private static String idPath(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    // ------------------------------------------------------------------ defaults (copied from the plugin)

    private static LinkedHashMap<String, String> defaultDurations() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("PLAYER_KILL", "24h");      // killed by another player
        m.put("MOB_ATTACK", "3h");        // killed by a mob's melee or projectile
        m.put("FALL", "2h");
        m.put("LAVA", "1h");
        m.put("FIRE", "1h");
        m.put("FIRE_TICK", "1h");
        m.put("DROWNING", "1h");
        m.put("STARVATION", "1h");
        m.put("SUFFOCATION", "1h");
        m.put("VOID", "30m");
        m.put("BLOCK_EXPLOSION", "2h");
        m.put("ENTITY_EXPLOSION", "2h");
        m.put("CONTACT", "1h");           // cactus / berry bush
        m.put("LIGHTNING", "1h");
        m.put("MAGIC", "1h");
        m.put("POISON", "1h");            // see README: poison and magic share a damage type
        m.put("WITHER", "2h");
        m.put("FREEZE", "1h");
        m.put("SUICIDE", "30m");          // /kill
        m.put("DEFAULT", "1h");
        return m;
    }

    private static LinkedHashMap<String, List<String>> defaultCauses() {
        LinkedHashMap<String, List<String>> m = new LinkedHashMap<>();
        m.put("MOB_ATTACK", mc("mob_attack", "mob_attack_no_aggro", "mob_projectile", "arrow", "trident",
                "thrown", "sting", "spit", "wind_charge", "fireball"));
        m.put("FALL", mc("fall"));
        m.put("LAVA", mc("lava"));
        m.put("FIRE", mc("in_fire"));
        m.put("FIRE_TICK", mc("on_fire"));
        m.put("DROWNING", mc("drown"));
        m.put("STARVATION", mc("starve"));
        m.put("SUFFOCATION", mc("in_wall"));
        m.put("VOID", mc("out_of_world"));
        m.put("CONTACT", mc("cactus", "sweet_berry_bush"));
        m.put("LIGHTNING", mc("lightning_bolt"));
        m.put("MAGIC", mc("magic", "indirect_magic"));
        m.put("WITHER", mc("wither"));
        m.put("FREEZE", mc("freeze"));
        m.put("SUICIDE", mc("generic_kill"));
        return m;
    }

    private static List<String> mc(String... ids) {
        List<String> l = new ArrayList<>();
        for (String s : ids) l.add("minecraft:" + s);
        return l;
    }

    // ------------------------------------------------------------------ io

    public static DynamicDeathBanConfig load() {
        try {
            if (Files.exists(FILE)) {
                DynamicDeathBanConfig c = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                        DynamicDeathBanConfig.class);
                if (c != null) return c;
            }
        } catch (Exception e) {
            DynamicDeathBan.LOG.error("Could not read dynamicdeathban.json, using defaults", e);
            return new DynamicDeathBanConfig();
        }
        DynamicDeathBanConfig c = new DynamicDeathBanConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            DynamicDeathBan.LOG.error("Could not write dynamicdeathban.json", e);
        }
    }
}
