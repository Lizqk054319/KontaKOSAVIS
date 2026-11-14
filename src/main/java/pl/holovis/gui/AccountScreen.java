package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.Accounts;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.PostFx;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Konto KosaVisuals: tworzenie konta (nazwa + główka skina), kod właściciela,
 * a dla właściciela panel z graczami (rangi VIP / FRIEND) i listą funkcji.
 */
public class AccountScreen extends Screen {
    private static final int W = 340, H = 330, ROWH = 18;
    private static final int GOLD = 0xFFC83D, GOOD = 0x5CE08A, BAD = 0xFF6B7A, GRAY = 0x8A93A6;

    private float sc = 1f;
    private int vw, vh;
    private int focus = 0;            // 0 brak, 1 nazwa, 2 kod
    private String nameIn = "", codeIn = "";
    private String status = "";
    private int statusCol = GOOD;
    private int tab = 0, scroll = 0;

    private record Feature(String label, BooleanSupplier on) {}

    public AccountScreen() { super(Text.literal("Konto")); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void close() { if (client != null) client.setScreen(new HoloScreen()); }

    @Override
    protected void init() {
        sc = MathHelper.clamp(Math.min((width - 12f) / W, (height - 12f) / H), 0.5f, 1f);
        vw = (int) (width / sc);
        vh = (int) (height / sc);
        if (!Accounts.has()) focus = 1;
    }

    private int ox() { return (vw - W) / 2; }
    private int oy() { return (vh - H) / 2; }
    private boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private void t(DrawContext c, String s, int x, int y, int rgb) { Gfx.text(c, textRenderer, s, x, y, 0xFF000000 | rgb, false); }

    private List<Feature> features() {
        HoloConfig c = HoloConfig.get();
        List<Feature> l = new ArrayList<>();
        l.add(new Feature("Hitbox Customizer", () -> c.hitboxEnabled));
        l.add(new Feature("Target ESP", () -> c.targetEsp));
        l.add(new Feature("Hit Color", () -> c.hitColor));
        l.add(new Feature("Kill Effect", () -> c.killFx));
        l.add(new Feature("Damage Numbers", () -> c.dmgNumbers));
        l.add(new Feature("Totem POP", () -> c.totemSound.enabled));
        l.add(new Feature("Swing Animations", () -> c.swingOn));
        l.add(new Feature("Hurt Cam", () -> c.hurtCamOn));
        l.add(new Feature("Knife", () -> c.knifeOn));
        l.add(new Feature("Hit Sound", () -> c.hitSound.enabled));
        l.add(new Feature("Kill Sound", () -> c.killSound.enabled));
        l.add(new Feature("Custom Sky", () -> c.skyEnabled));
        l.add(new Feature("Motion Blur", () -> c.motionBlur));
        l.add(new Feature("Color Saturation", () -> c.colorGrade));
        l.add(new Feature("Halo", () -> c.halo));
        l.add(new Feature("Jump Circles", () -> c.jumpCircles));
        l.add(new Feature("World Particles", () -> c.worldParticles));
        l.add(new Feature("Aspect Ratio", () -> c.aspectOn));
        l.add(new Feature("FullBright", () -> c.fullbright));
        l.add(new Feature("Watermark", () -> c.watermark));
        l.add(new Feature("Koordynaty", () -> c.coords));
        l.add(new Feature("Potions", () -> c.potions));
        l.add(new Feature("Armor Status", () -> c.armorStatus));
        l.add(new Feature("TargetHUD", () -> c.targetHud));
        l.add(new Feature("Spotify", () -> c.spotify));
        l.add(new Feature("Cooldowns", () -> c.cooldowns));
        l.add(new Feature("Kosa Nametag", () -> c.kosaTag));
        return l;
    }

    private List<String> players() {
        List<String> l = new ArrayList<>(Accounts.seen());
        Collections.reverse(l);
        return l;
    }

    private void field(DrawContext c, int x, int y, int w, int h, String val, String hint, boolean foc, int accent) {
        Gfx.rrect(c, x, y, w, h, 5, 0xE00A0D14);
        c.drawBorder(x, y, w, h, Gfx.argb(accent, foc ? 0.85f : 0.25f));
        boolean empty = val.isEmpty() && !foc;
        String s = empty ? hint : val + (foc && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        t(c, textRenderer.trimToWidth(s, w - 12), x + 6, y + (h - 8) / 2, empty ? 0x5E6678 : 0xFFFFFF);
    }

    private void btn(DrawContext c, int x, int y, int w, int h, String s, int col, boolean hov, boolean on) {
        Gfx.rrect(c, x, y, w, h, 5, Gfx.argb(col, on ? 0.9f : (hov ? 0.55f : 0.28f)));
        t(c, s, x + (w - textRenderer.getWidth(s)) / 2, y + (h - 8) / 2, 0xFFFFFF);
    }

    @Override
    public void render(DrawContext c, int rawMx, int rawMy, float delta) {
        HoloConfig cfg = HoloConfig.get();
        int accent = cfg.guiColor.rgb();
        PostFx.blurBackground(c, cfg.clientBlur);
        c.fill(0, 0, width, height, 0x66000000);
        c.getMatrices().push();
        c.getMatrices().scale(sc, sc, 1f);
        int mx = (int) (rawMx / sc), my = (int) (rawMy / sc);
        int x = ox(), y = oy();
        boolean owner = Accounts.isOwner();
        int edge = owner ? GOLD : accent;

        Gfx.rrect(c, x - 1, y - 1, W + 2, H + 2, 13, Gfx.argb(edge, 0.35f));
        Gfx.rrect(c, x, y, W, H, 12, 0xF510141D);
        Gfx.rboxV(c, x, y, W, 46, 12, Gfx.argb(edge, 0.14f), Gfx.argb(edge, 0f));
        c.getMatrices().push();
        c.getMatrices().translate(x + 18, y + 14, 0f);
        c.getMatrices().scale(1.5f, 1.5f, 1f);
        t(c, "Konto KosaVisuals", 0, 0, 0xFFFFFF);
        c.getMatrices().pop();
        btn(c, x + W - 34, y + 12, 22, 22, "x", 0x3A4254, in(mx, my, x + W - 34, y + 12, 22, 22), false);

        int hx = x + 18, hy = y + 48;
        if (client != null && client.player != null) {
            Gfx.rrect(c, hx - 2, hy - 2, 36, 36, 6, Gfx.argb(edge, 0.9f));
            PlayerSkinDrawer.draw(c, client.player.getSkinTextures(), hx, hy, 32);
        }

        if (!Accounts.has()) {
            t(c, "Stwórz konto", hx + 44, hy + 2, 0xFFFFFF);
            t(c, "Twoja główka skina będzie obok nazwy.", hx + 44, hy + 16, GRAY);
            field(c, x + 18, y + 96, W - 36, 22, nameIn, "Nazwa konta (3-16 znaków)", focus == 1, accent);
            btn(c, x + 18, y + 126, W - 36, 24, "Stwórz konto", accent, in(mx, my, x + 18, y + 126, W - 36, 24), true);
        } else {
            c.getMatrices().push();
            c.getMatrices().translate(hx + 44, hy + 2, 0f);
            c.getMatrices().scale(1.4f, 1.4f, 1f);
            t(c, Accounts.name(), 0, 0, owner ? GOLD : 0xFFFFFF);
            c.getMatrices().pop();
            String badge = owner ? "WŁAŚCICIEL" : "Użytkownik";
            int bw = textRenderer.getWidth(badge) + 10;
            Gfx.rrect(c, hx + 44, hy + 19, bw, 12, 4, Gfx.argb(owner ? GOLD : accent, owner ? 0.85f : 0.35f));
            t(c, badge, hx + 49, hy + 21, owner ? 0x1A1405 : 0xFFFFFF);
            btn(c, x + W - 96, hy + 4, 78, 18, "Usuń konto", 0x7A2E3A, in(mx, my, x + W - 96, hy + 4, 78, 18), false);

            if (!owner) {
                t(c, "Właściciel kod", x + 18, y + 92, GRAY);
                field(c, x + 100, y + 88, W - 160, 16, codeIn, "wpisz kod...", focus == 2, accent);
                btn(c, x + W - 54, y + 88, 36, 16, "OK", accent, in(mx, my, x + W - 54, y + 88, 36, 16), false);
                t(c, "Rangi i panel właściciela odblokowuje", x + 18, y + 124, 0x5E6678);
                t(c, "kod właściciela.", x + 18, y + 135, 0x5E6678);
            } else {
                // zakładki
                String[] tabs = {"Gracze", "Funkcje"};
                int tw = (W - 36) / 2;
                for (int i = 0; i < 2; i++) {
                    int tx = x + 18 + i * tw;
                    btn(c, tx, y + 90, tw - 4, 18, tabs[i], tab == i ? GOLD : 0x3A4254, in(mx, my, tx, y + 90, tw - 4, 18), tab == i);
                }
                int ly = y + 114, lh = H - 114 - 30, visible = lh / ROWH;
                Gfx.rrect(c, x + 14, ly, W - 28, lh, 8, 0x40000000);
                if (tab == 0) {
                    List<String> pl = players();
                    if (pl.isEmpty()) t(c, "Brak graczy - wejdź na serwer, lista zapełni się sama.", x + 22, ly + 8, GRAY);
                    scroll = MathHelper.clamp(scroll, 0, Math.max(0, pl.size() - visible));
                    for (int i = 0; i < Math.min(visible, pl.size() - scroll); i++) {
                        String n = pl.get(i + scroll);
                        int ry = ly + 3 + i * ROWH;
                        String r = Accounts.rank(n);
                        t(c, textRenderer.trimToWidth(n, 130), x + 22, ry + 5, r.isEmpty() ? 0xE8EEF8 : Accounts.rankColor(r));
                        int bx = x + W - 22 - 3 * 50;
                        for (int k = 0; k < 3; k++) {
                            String lab = k == 0 ? "-" : Accounts.RANKS[k];
                            int col = k == 0 ? 0x3A4254 : Accounts.RANK_COLORS[k];
                            boolean on = Accounts.RANKS[k].equals(r);
                            btn(c, bx + k * 50, ry + 1, 46, 14, lab, col, in(mx, my, bx + k * 50, ry + 1, 46, 14), on);
                        }
                    }
                } else {
                    List<Feature> fs = features();
                    scroll = MathHelper.clamp(scroll, 0, Math.max(0, fs.size() - visible));
                    for (int i = 0; i < Math.min(visible, fs.size() - scroll); i++) {
                        Feature f = fs.get(i + scroll);
                        int ry = ly + 3 + i * ROWH;
                        boolean on = f.on().getAsBoolean();
                        t(c, f.label(), x + 22, ry + 5, 0xE8EEF8);
                        String st = on ? "WŁ." : "WYŁ.";
                        Gfx.rrect(c, x + W - 66, ry + 2, 44, 13, 4, Gfx.argb(on ? GOOD : 0x3A4254, on ? 0.8f : 0.6f));
                        t(c, st, x + W - 44 - textRenderer.getWidth(st) / 2, ry + 5, 0xFFFFFF);
                    }
                }
                t(c, "Rangi widzisz tylko Ty (mod działa bez serwera).", x + 18, y + H - 24, 0x5E6678);
            }
        }
        if (!status.isEmpty()) t(c, textRenderer.trimToWidth(status, W - 36), x + 18, y + H - 12, statusCol);
        c.getMatrices().pop();
    }

    private void createAccount() {
        String err = Accounts.create(nameIn);
        if (err != null) { status = err; statusCol = BAD; return; }
        status = "Konto utworzone!"; statusCol = GOOD; focus = 0;
    }

    private void redeem() {
        if (Accounts.redeem(codeIn)) { status = "Kod poprawny - masz konto właściciela!"; statusCol = GOLD; codeIn = ""; focus = 0; }
        else { status = "Niepoprawny kod"; statusCol = BAD; }
    }

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mx = rawX / sc, my = rawY / sc;
        int x = ox(), y = oy();
        if (!in(mx, my, x, y, W, H) || in(mx, my, x + W - 34, y + 12, 22, 22)) { close(); return true; }
        if (!Accounts.has()) {
            if (in(mx, my, x + 18, y + 96, W - 36, 22)) { focus = 1; return true; }
            if (in(mx, my, x + 18, y + 126, W - 36, 24)) { createAccount(); return true; }
            focus = 0;
            return true;
        }
        int hy = y + 48;
        if (in(mx, my, x + W - 96, hy + 4, 78, 18)) {
            Accounts.delete(); nameIn = ""; codeIn = ""; focus = 1; status = "Konto usunięte"; statusCol = BAD;
            return true;
        }
        if (!Accounts.isOwner()) {
            if (in(mx, my, x + 100, y + 88, W - 160, 16)) { focus = 2; return true; }
            if (in(mx, my, x + W - 54, y + 88, 36, 16)) { redeem(); return true; }
            focus = 0;
            return true;
        }
        int tw = (W - 36) / 2;
        for (int i = 0; i < 2; i++) if (in(mx, my, x + 18 + i * tw, y + 90, tw - 4, 18)) { tab = i; scroll = 0; return true; }
        if (tab == 0) {
            int ly = y + 114, lh = H - 114 - 30, visible = lh / ROWH;
            List<String> pl = players();
            for (int i = 0; i < Math.min(visible, pl.size() - scroll); i++) {
                int ry = ly + 3 + i * ROWH;
                int bx = x + W - 22 - 3 * 50;
                for (int k = 0; k < 3; k++) {
                    if (in(mx, my, bx + k * 50, ry + 1, 46, 14)) {
                        Accounts.setRank(pl.get(i + scroll), Accounts.RANKS[k]);
                        return true;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (int) Math.signum(verticalAmount) * 2);
        return true;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (ch < 32) return false;
        if (focus == 1 && nameIn.length() < 16 && (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.')) { nameIn += ch; return true; }
        if (focus == 2 && codeIn.length() < 40) { codeIn += ch; return true; }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (focus == 1 && !nameIn.isEmpty()) nameIn = nameIn.substring(0, nameIn.length() - 1);
            if (focus == 2 && !codeIn.isEmpty()) codeIn = codeIn.substring(0, codeIn.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (focus == 1) createAccount();
            else if (focus == 2) redeem();
            return true;
        }
        if (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0 && client != null && focus != 0) {
            String clip = client.keyboard.getClipboard().trim();
            if (focus == 1) nameIn = (nameIn + clip).replaceAll("[^A-Za-z0-9_.-]", "");
            else codeIn = codeIn + clip;
            if (nameIn.length() > 16) nameIn = nameIn.substring(0, 16);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
