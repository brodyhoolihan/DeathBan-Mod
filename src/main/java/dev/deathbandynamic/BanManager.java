package dev.deathbandynamic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Stores bans per world in <world>/dynamicdeathban_data.json (survives restarts). */
public class BanManager {
    public static class Entry {
        public String name;
        public long expiry;   // epoch millis, Long.MAX_VALUE = permanent, 0 = not banned
        public String cause;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<String, Entry> data;

    private BanManager(Path file, Map<String, Entry> data) {
        this.file = file;
        this.data = data;
    }

    public static BanManager load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("dynamicdeathban_data.json");
        Map<String, Entry> map = new HashMap<>();
        try {
            if (Files.exists(file)) {
                Map<String, Entry> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Entry>>() {}.getType());
                if (loaded != null) map = loaded;
            }
        } catch (Exception e) {
            DynamicDeathBan.LOG.error("Could not read dynamicdeathban_data.json", e);
        }
        return new BanManager(file, map);
    }

    public synchronized Entry get(UUID id, String name) {
        Entry e = data.computeIfAbsent(id.toString(), k -> new Entry());
        e.name = name;
        return e;
    }

    /** Only returns the entry while the ban is still running. */
    public synchronized Entry active(UUID id) {
        Entry e = data.get(id.toString());
        return e != null && e.expiry > System.currentTimeMillis() ? e : null;
    }

    public synchronized Entry findByName(String name) {
        for (Entry e : data.values()) if (e.name != null && e.name.equalsIgnoreCase(name)) return e;
        return null;
    }

    public synchronized List<String> names() {
        List<String> out = new ArrayList<>();
        for (Entry e : data.values()) if (e.name != null) out.add(e.name);
        return out;
    }

    /** Millis left, or -1 if permanent. */
    public static long remaining(Entry e) {
        if (e.expiry == Long.MAX_VALUE) return DurationUtil.PERMANENT;
        return Math.max(0, e.expiry - System.currentTimeMillis());
    }

    public synchronized void save() {
        try {
            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            DynamicDeathBan.LOG.error("Could not write dynamicdeathban_data.json", e);
        }
    }
}
