package dev.smolagent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings edited through /smolpp, stored in config/smol-agent.json.
 * The lists are edited in place by the screen, so changes apply live; {@link #save()} writes them out.
 */
public final class SmolConfig {

    public static final int MIN_OPACITY = 1;
    public static final int MAX_OPACITY = 100;

    public static final class Rename {
        public String from = "";
        public String to = "";

        public Rename() {}

        public Rename(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    public List<Rename> renames = new ArrayList<>();
    public List<String> translucent = new ArrayList<>();
    public int opacity = 30;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SmolConfig instance;

    public static SmolConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("smol-agent.json");
    }

    private static SmolConfig load() {
        Path p = path();
        if (Files.exists(p)) {
            try {
                SmolConfig c = GSON.fromJson(Files.readString(p), SmolConfig.class);
                if (c != null) return c.sanitized();
            } catch (Exception e) {
                SmolAgentMod.LOG.warn("Could not read {}, using defaults", p, e);
            }
        }
        // First start: keep the behaviour of the old hard-coded version.
        SmolConfig c = new SmolConfig();
        c.renames.add(new Rename("Agent", "Smol"));
        c.translucent.add("agent");
        c.translucent.add("smol");
        return c;
    }

    private SmolConfig sanitized() {
        if (renames == null) renames = new ArrayList<>();
        if (translucent == null) translucent = new ArrayList<>();
        renames.removeIf(r -> r == null);
        renames.forEach(r -> {
            if (r.from == null) r.from = "";
            if (r.to == null) r.to = "";
        });
        translucent.removeIf(t -> t == null);
        opacity = Math.clamp(opacity, MIN_OPACITY, MAX_OPACITY);
        return this;
    }

    /** Drops empty rows and writes the file. */
    public void save() {
        renames.removeIf(r -> r.from.isEmpty());
        translucent.removeIf(String::isBlank);
        opacity = Math.clamp(opacity, MIN_OPACITY, MAX_OPACITY);
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            SmolAgentMod.LOG.error("Could not write {}", path(), e);
        }
    }

    public List<NameRules.Rename> renameRules() {
        List<NameRules.Rename> out = new ArrayList<>(renames.size());
        for (Rename r : renames) out.add(new NameRules.Rename(r.from, r.to));
        return out;
    }

    /** Alpha multiplier for players that match {@link #translucent}; 1 means opaque. */
    public float alpha() {
        return Math.clamp(opacity, MIN_OPACITY, MAX_OPACITY) / 100f;
    }
}
