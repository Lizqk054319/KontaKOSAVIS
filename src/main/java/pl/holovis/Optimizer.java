package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.SimpleOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Optymalizacja (/kosa optymalizacja): przegląda ustawienia grafiki Minecrafta i obniża te,
 * które zabierają najwięcej FPS, a mało wnoszą. Trzy poziomy: Medium, Hard, Ultra.
 * Niczego nie podnosi - jeśli masz już niżej, zostaje. Zmiany można cofnąć przyciskiem "Przywróć".
 */
public final class Optimizer {
    private Optimizer() {}

    public static final String[] LEVELS = {"Medium", "Hard", "Ultra", "Extreme"};

    private static final List<Runnable> UNDO = new ArrayList<>();

    public static boolean canRestore() { return !UNDO.isEmpty(); }

    private interface Fmt<T> { String f(T v); }

    /** Porównuje i (jeśli apply) ustawia jedną opcję. */
    private static <T> void opt(List<String> out, boolean apply, List<Runnable> undo, String label,
                                SimpleOption<T> o, T target, boolean change, Fmt<T> fmt) {
        if (o == null) return;
        T cur = o.getValue();
        if (!change || cur.equals(target)) return;
        out.add(label + ": " + fmt.f(cur) + " → " + fmt.f(target));
        if (apply) {
            undo.add(() -> o.setValue(cur));
            o.setValue(target);
        }
    }

    private static String onOff(Boolean b) { return b ? "wł." : "wył."; }

    private static String gfx(GraphicsMode g) {
        return switch (g) { case FAST -> "Szybka"; case FANCY -> "Dokładna"; default -> "Najlepsza"; };
    }

    private static String clouds(CloudRenderMode c) {
        return switch (c) { case OFF -> "Wył."; case FAST -> "Szybkie"; default -> "Dokładne"; };
    }

    private static String parts(Object p) {
        String n = ((Enum<?>) p).name();
        return n.equals("MINIMAL") ? "Minimalne" : n.equals("DECREASED") ? "Zmniejszone" : "Wszystkie";
    }

    /** Stała enuma o danej nazwie z typu opcji (bez importowania klasy enuma). */
    @SuppressWarnings("unchecked")
    private static <T> T named(SimpleOption<T> o, String name) {
        T cur = o.getValue();
        for (Object e : ((Enum<?>) cur).getDeclaringClass().getEnumConstants())
            if (((Enum<?>) e).name().equals(name)) return (T) e;
        return cur;
    }

    private static int ord(Object e) { return ((Enum<?>) e).ordinal(); }

    /** Lista zmian dla poziomu (0 Medium, 1 Hard, 2 Ultra). apply = true wprowadza je. */
    public static List<String> run(int level, boolean apply, boolean kosaFx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        GameOptions o = mc.options;
        List<String> out = new ArrayList<>();
        List<Runnable> undo = new ArrayList<>();
        int L = Math.max(0, Math.min(3, level));

        int rd = new int[]{12, 8, 4, 2}[L];
        int sd = new int[]{8, 6, 5, 5}[L];
        int bb = new int[]{2, 0, 0, 0}[L];
        double ed = new double[]{0.75, 0.6, 0.5, 0.5}[L];

        opt(out, apply, undo, "Zasięg renderowania", o.getViewDistance(), rd, o.getViewDistance().getValue() > rd, v -> v + " ch.");
        opt(out, apply, undo, "Zasięg symulacji", o.getSimulationDistance(), sd, o.getSimulationDistance().getValue() > sd, v -> v + " ch.");
        opt(out, apply, undo, "Grafika", o.getGraphicsMode(), GraphicsMode.FAST, o.getGraphicsMode().getValue() != GraphicsMode.FAST, Optimizer::gfx);
        CloudRenderMode cl = L == 0 ? CloudRenderMode.FAST : CloudRenderMode.OFF;
        opt(out, apply, undo, "Chmury", o.getCloudRenderMode(), cl,
                o.getCloudRenderMode().getValue().ordinal() > cl.ordinal() || (L > 0 && o.getCloudRenderMode().getValue() != CloudRenderMode.OFF), Optimizer::clouds);
        var pm = named(o.getParticles(), L == 0 ? "DECREASED" : "MINIMAL");
        opt(out, apply, undo, "Cząsteczki", o.getParticles(), pm, ord(o.getParticles().getValue()) < ord(pm), Optimizer::parts);
        opt(out, apply, undo, "Cienie encji", o.getEntityShadows(), false, true, Optimizer::onOff);
        opt(out, apply, undo, "Mieszanie biomów", o.getBiomeBlendRadius(), bb, o.getBiomeBlendRadius().getValue() > bb, v -> String.valueOf(v));
        opt(out, apply, undo, "Odległość encji", o.getEntityDistanceScaling(), ed, o.getEntityDistanceScaling().getValue() > ed + 0.001,
                v -> Math.round(v * 100) + "%");
        opt(out, apply, undo, "VSync", o.getEnableVsync(), false, true, Optimizer::onOff);
        opt(out, apply, undo, "Limit FPS", o.getMaxFps(), 260, o.getMaxFps().getValue() < 260, v -> v >= 260 ? "Bez limitu" : String.valueOf(v));
        opt(out, apply, undo, "Rozmycie tła menu", o.getMenuBackgroundBlurriness(), 0, o.getMenuBackgroundBlurriness().getValue() > 0, v -> String.valueOf(v));
        if (L >= 1) {
            opt(out, apply, undo, "Gładkie oświetlenie", o.getAo(), false, true, Optimizer::onOff);
            var none = named(o.getChunkBuilderMode(), "NONE");
            opt(out, apply, undo, "Budowanie chunków", o.getChunkBuilderMode(), none, true,
                    v -> ((Enum<?>) v).name().equals("NONE") ? "Wątki (szybkie)" : "Blokujące");
        }

        HoloConfig c = HoloConfig.get();
        if (kosaFx) {
            if (c.motionBlur) { out.add("KosaVisuals: Motion Blur → wył."); if (apply) { boolean b = c.motionBlur; undo.add(() -> c.motionBlur = b); c.motionBlur = false; } }
            if (c.colorGrade && L >= 1) { out.add("KosaVisuals: Color Saturation → wył."); if (apply) { boolean b = c.colorGrade; undo.add(() -> c.colorGrade = b); c.colorGrade = false; } }
            if (c.clientBlur > 0f && L >= 1) { out.add("KosaVisuals: Rozmycie klienta → 0"); if (apply) { float b = c.clientBlur; undo.add(() -> c.clientBlur = b); c.clientBlur = 0f; } }
            if (L >= 3) {
                if (c.worldParticles) { out.add("KosaVisuals: World Particles \u2192 wył."); if (apply) { undo.add(() -> c.worldParticles = true); c.worldParticles = false; } }
                if (c.jumpCircles) { out.add("KosaVisuals: Jump Circles \u2192 wył."); if (apply) { undo.add(() -> c.jumpCircles = true); c.jumpCircles = false; } }
                if (c.halo) { out.add("KosaVisuals: Halo \u2192 wył."); if (apply) { undo.add(() -> c.halo = true); c.halo = false; } }
                if (c.skyEnabled) { out.add("KosaVisuals: Custom Sky \u2192 wył."); if (apply) { undo.add(() -> c.skyEnabled = true); c.skyEnabled = false; } }
            }
        }

        if (apply) {
            if (!undo.isEmpty()) { UNDO.clear(); UNDO.addAll(undo); }
            o.write();
            HoloConfig.save();
            if (mc.worldRenderer != null) mc.worldRenderer.reload();
            System.gc();
        }
        return out;
    }

    public static void restore() {
        if (UNDO.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Runnable r : UNDO) {
            try { r.run(); } catch (Exception ignored) {}
        }
        UNDO.clear();
        mc.options.write();
        HoloConfig.save();
        if (mc.worldRenderer != null) mc.worldRenderer.reload();
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Windows: podnosi priorytet procesu Minecrafta do "Wysoki" (system daje grze więcej czasu procesora). */
    public static void highPriority() {
        if (!isWindows()) return;
        long pid = ProcessHandle.current().pid();
        Thread t = new Thread(() -> {
            try {
                new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                        "(Get-Process -Id " + pid + ").PriorityClass = 'High'").redirectErrorStream(true).start().waitFor();
            } catch (Exception e) {
                System.err.println("[KosaVisuals] Nie udało się zmienić priorytetu: " + e);
            }
        }, "holovis-priority");
        t.setDaemon(true);
        t.start();
    }
}
