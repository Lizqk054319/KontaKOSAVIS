package pl.holovis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Konto KosaVisuals (lokalne): nazwa, opcjonalnie konto właściciela (kod) i rangi nadawane graczom.
 * Wszystko zapisuje się tylko na tym komputerze (config/holovis/account.json) - bez serwera mod nie może
 * wysłać rang innym graczom ani sprawdzić, kto jeszcze używa KosaVisuals.
 */
public final class Accounts {
    private Accounts() {}

    public static final String[] RANKS = {"", "VIP", "FRIEND"};
    public static final int[] RANK_COLORS = {0xFFFFFF, 0xFFD23C, 0xFF7AC8};

    /** Skróty SHA-256 kodów właściciela (same kody nie są zapisane w kodzie źródłowym). */
    private static final String[] OWNER_HASHES = {
            "70f0400402c86a610c6726ac097c97336f47f4dd0c4eb3ff846ab16d1cc12566",
            "cadcd2d445bf9bf32006a309a3469dabb78b05f1a6c4e926fe01c5b723217a55"};

    private static final class Data {
        String name = "";
        long created;
        boolean owner;
        Map<String, String> ranks = new LinkedHashMap<>();
        List<String> seen = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("holovis").resolve("account.json");
    private static Data data;
    private static long lastScan;

    private static Data d() {
        if (data != null) return data;
        data = new Data();
        try {
            if (Files.exists(PATH)) {
                Data x = GSON.fromJson(Files.readString(PATH), Data.class);
                if (x != null) {
                    if (x.ranks == null) x.ranks = new LinkedHashMap<>();
                    if (x.seen == null) x.seen = new ArrayList<>();
                    if (x.name == null) x.name = "";
                    data = x;
                }
            }
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się wczytać konta: " + e);
        }
        return data;
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(d()));
        } catch (Exception e) {
            System.err.println("[KosaVisuals] Nie udało się zapisać konta: " + e);
        }
    }

    public static boolean has() { return !d().name.isEmpty(); }
    public static String name() { return d().name; }
    public static boolean isOwner() { return has() && d().owner; }

    /** @return null gdy OK, inaczej komunikat */
    public static String create(String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() < 3) return "Nazwa musi mieć min. 3 znaki";
        if (n.length() > 16) n = n.substring(0, 16);
        d().name = n;
        d().created = System.currentTimeMillis();
        save();
        return null;
    }

    public static void delete() {
        data = new Data();
        save();
    }

    private static String sha(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** @return true, gdy kod jest poprawny (konto staje się kontem właściciela) */
    public static boolean redeem(String code) {
        String h = sha(code == null ? "" : code.trim());
        for (String o : OWNER_HASHES) {
            if (o.equals(h)) {
                d().owner = true;
                save();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ rangi i gracze
    public static String rank(String player) {
        if (player == null) return "";
        String r = d().ranks.get(player.toLowerCase(Locale.ROOT));
        return r == null ? "" : r;
    }

    public static int rankColor(String rank) {
        for (int i = 0; i < RANKS.length; i++) if (RANKS[i].equals(rank)) return RANK_COLORS[i];
        return 0xFFFFFF;
    }

    public static void setRank(String player, String rank) {
        String k = player.toLowerCase(Locale.ROOT);
        if (rank == null || rank.isEmpty()) d().ranks.remove(k); else d().ranks.put(k, rank);
        save();
    }

    /** Gracze spotkani na serwerach (z listy TAB), najnowsi na końcu. */
    public static List<String> seen() { return d().seen; }

    /** Co kilka sekund zapamiętuje graczy z listy TAB. */
    public static void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (now - lastScan < 5000 || mc.getNetworkHandler() == null) return;
        lastScan = now;
        boolean changed = false;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().getName();
            if (n == null || n.isEmpty()) continue;
            List<String> s = d().seen;
            if (!s.contains(n)) {
                s.add(n);
                if (s.size() > 300) s.remove(0);
                changed = true;
            }
        }
        if (changed) save();
    }
}
