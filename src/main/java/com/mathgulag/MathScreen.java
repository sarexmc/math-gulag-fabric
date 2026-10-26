package com.mathgulag;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;

public class MathScreen extends Screen {
    static final Identifier VEINS = Identifier.of("mathgulag", "textures/gui/veins.png");
    int a, b, seed, flash, tick; String typed; boolean built = false;
    final int[][] pos = new int[10][2];

    public MathScreen(OpenPayload p) { super(Text.literal("MathGulag")); apply(p); }
    void apply(OpenPayload p) { a = p.a(); b = p.b(); seed = p.seed(); typed = p.typed(); flash = p.flash(); if (flash > 0) tick = 0; built = false; }
    public void update(OpenPayload p) { apply(p); layout(); }

    @Override protected void init() { layout(); }
    void layout() {
        if (width == 0) return;
        Random r = new Random(seed);
        List<Integer> order = new ArrayList<>(); for (int i = 0; i < 10; i++) order.add(i);
        Collections.shuffle(order, r);
        for (int k = 0; k < 10; k++) {
            double ang = Math.toRadians(k * 36 + r.nextInt(14) - 7 + 18);
            int cx = (int) (width / 2.0 + Math.cos(ang) * width * 0.40), cy = (int) (height * 0.58 + Math.sin(ang) * height * 0.33);
            cx = Math.max(40, Math.min(width - 40, cx)); cy = Math.max(110, Math.min(height - 40, cy));
            pos[order.get(k)][0] = cx; pos[order.get(k)][1] = cy;
        }
        built = true;
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext c, int mx, int my, float d) { }
    @Override public void tick() { tick++; }

    void glyph(DrawContext c, String name, int x, int y, int w, int h, int tw, int th) {
        c.drawTexture(Identifier.of("mathgulag", "textures/font/" + name + ".png"), x, y, w, h, 0, 0, tw, th, tw, th);
    }
    void bigText(DrawContext c, String s, int cx, int y) {
        int w = 34, total = s.length() * w, x = cx - total / 2;
        for (char ch : s.toCharArray()) {
            String n = ch == 'x' ? "nx" : ch == '=' ? "neq" : ch == '?' ? "nq" : ch == ' ' ? null : "n" + ch;
            if (n != null) glyph(c, n, x, y, 30, 40, 96, 128); x += w;
        }
    }
    @Override public void render(DrawContext c, int mx, int my, float delta) {
        if (!built) layout();
        float t = tick + delta;
        c.fill(0, 0, width, height, 0x66200000);
        double s = Math.sin(t * 0.3); float beat = (float) (0.55 + 0.45 * Math.pow(Math.max(0, s), 3));
        RenderSystem.enableBlend();
        c.setShaderColor(1f, 0.2f, 0.2f, beat);
        c.drawTexture(VEINS, 0, 0, width, height, 0, 0, 512, 512, 512, 512);
        c.setShaderColor(1, 1, 1, 1);
        if (flash > 0 && tick < 25) c.fill(0, 0, width, height, 0x8800AA22);
        bigText(c, a + " x " + b, width / 2, 14);
        bigText(c, "= " + typed, width / 2, 60);
        for (int i = 0; i < 10; i++) {
            boolean hov = Math.hypot(mx - pos[i][0], my - pos[i][1]) < 28;
            int sz = (int) (50 + Math.sin(t * 0.3 + i) * 3 + (hov ? 10 : 0));
            glyph(c, "b" + i, pos[i][0] - sz / 2, pos[i][1] - sz / 2, sz, sz, 72, 72);
        }
        RenderSystem.disableBlend();
    }
    @Override public boolean mouseClicked(double mx, double my, int btn) {
        for (int i = 0; i < 10; i++)
            if (Math.hypot(mx - pos[i][0], my - pos[i][1]) < 28) { ClientPlayNetworking.send(new AnswerPayload(i)); return true; }
        return super.mouseClicked(mx, my, btn);
    }
}
