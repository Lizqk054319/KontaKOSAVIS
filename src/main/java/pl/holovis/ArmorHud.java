package pl.holovis;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Armor Status: założona zbroja z paskiem i procentem wytrzymałości (zielony -> czerwony). */
public final class ArmorHud {
    private ArmorHud() {}

    private static final int ROW = 20, GAP = 2, W = 96;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static List<ItemStack> items(boolean preview) {
        List<ItemStack> l = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            for (EquipmentSlot s : SLOTS) {
                ItemStack st = mc.player.getEquippedStack(s);
                if (!st.isEmpty()) l.add(st);
            }
            HoloConfig c = HoloConfig.get();
            if (c.armorHands) {
                if (mc.player.getMainHandStack().isDamageable()) l.add(mc.player.getMainHandStack());
                if (mc.player.getOffHandStack().isDamageable()) l.add(mc.player.getOffHandStack());
            }
        }
        if (l.isEmpty() && preview) {
            ItemStack a = new ItemStack(Items.DIAMOND_HELMET), b = new ItemStack(Items.DIAMOND_CHESTPLATE),
                    d = new ItemStack(Items.IRON_LEGGINGS), e = new ItemStack(Items.NETHERITE_BOOTS);
            b.setDamage(b.getMaxDamage() / 2);
            d.setDamage(d.getMaxDamage() * 4 / 5);
            l.add(a); l.add(b); l.add(d); l.add(e);
        }
        return l;
    }

    public static boolean visible() { return !items(false).isEmpty(); }

    public static int[] size() {
        int n = Math.max(1, items(true).size());
        return new int[]{W, n * ROW + (n - 1) * GAP};
    }

    /** Kolor wg wytrzymałości: zielony -> żółty -> czerwony. */
    private static int dur(float f) {
        return f > 0.5f ? Gfx.mix(0xFFD23C, 0x4CE07A, (f - 0.5f) * 2f) : Gfx.mix(0xFF3C4E, 0xFFD23C, f * 2f);
    }

    public static void draw(DrawContext ctx, int x, int y, boolean preview) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        List<ItemStack> l = items(preview);
        for (int i = 0; i < l.size(); i++) {
            ItemStack st = l.get(i);
            int ry = y + i * (ROW + GAP);
            int max = st.getMaxDamage();
            float f = max > 0 ? 1f - st.getDamage() / (float) max : 1f;
            int col = dur(f);
            boolean low = f < 0.15f && (System.currentTimeMillis() / 350) % 2 == 0;

            Gfx.rrect(ctx, x, ry, W, ROW, 5, 0xB00B0E15);
            Gfx.rboxH(ctx, x, ry, Math.min(W, 54), ROW, 5, Gfx.argb(col, 0.20f), Gfx.argb(col, 0f));
            ctx.drawItem(st, x + 3, ry + 2);

            String pct = max > 0 ? Math.round(f * 100) + "%" : "∞";
            ctx.drawText(tr, pct, x + 23, ry + 3, low ? 0xFFFF5A5A : 0xFFF2F5FA, true);
            if (max > 0) {
                String left = String.valueOf(max - st.getDamage());
                ctx.drawText(tr, left, x + W - 5 - tr.getWidth(left), ry + 3, 0xFF8B94A8, true);
            }
            int bx = x + 23, bw = W - 28, by = ry + 14;
            Gfx.rrect(ctx, bx, by, bw, 3, 1, 0x40FFFFFF);
            int fw = Math.max(2, (int) (bw * f));
            Gfx.rrect(ctx, bx, by, fw, 3, 1, 0xFF000000 | col);
        }
    }
}
