package com.ahs;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Drag either panel (auction or papering) where you want it. Arrow keys nudge (hold Shift for 10px). */
public class HudMoveScreen extends Screen {
    private final Screen parent;
    private int target = 0; // 0 = auction panel, 1 = papering panel
    private boolean dragging;
    private int offX, offY;

    public HudMoveScreen(Screen parent) {
        super(Component.literal("Move HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AhsClient.menuOpen = true;
        addRenderableWidget(Button.builder(Component.literal("Reset positions"), b -> {
            AhsConfig.INSTANCE.hudX = 10;
            AhsConfig.INSTANCE.hudY = 10;
            AhsConfig.INSTANCE.papX = 10;
            AhsConfig.INSTANCE.papY = 70;
        }).bounds(width / 2 - 105, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 + 5, height - 28, 100, 20).build());
    }

    private PaperingHud.Model papModel() {
        return PaperingHud.isEmpty() ? PaperingHud.demo() : PaperingHud.live();
    }

    private int pw() { return (int) (HudRenderer.W * AhsConfig.INSTANCE.scale); }

    private int ph(int which) {
        AhsConfig c = AhsConfig.INSTANCE;
        int base = which == 0 ? HudRenderer.H : PaperingHud.height(papModel(), c);
        return (int) (base * c.scale);
    }

    private int getX(int which) { return which == 0 ? AhsConfig.INSTANCE.hudX : AhsConfig.INSTANCE.papX; }
    private int getY(int which) { return which == 0 ? AhsConfig.INSTANCE.hudY : AhsConfig.INSTANCE.papY; }

    private void setPos(int which, int x, int y) {
        x = Math.max(0, Math.min(x, width - pw()));
        y = Math.max(0, Math.min(y, height - ph(which)));
        if (which == 0) { AhsConfig.INSTANCE.hudX = x; AhsConfig.INSTANCE.hudY = y; }
        else { AhsConfig.INSTANCE.papX = x; AhsConfig.INSTANCE.papY = y; }
    }

    private boolean inside(int which, double mx, double my) {
        int x = getX(which), y = getY(which);
        return mx >= x && mx <= x + pw() && my >= y && my <= y + ph(which);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        AhsConfig c = AhsConfig.INSTANCE;
        setPos(0, c.hudX, c.hudY);
        setPos(1, c.papX, c.papY);

        HudRenderer.Model m = HudRenderer.live();
        HudRenderer.draw(g, m != null ? m : HudRenderer.demo(), c.hudX, c.hudY, c.scale, c);
        PaperingHud.draw(g, papModel(), c.papX, c.papY, c.scale, c);

        for (int i = 0; i < 2; i++) {
            int col = (i == target) ? (dragging ? 0xFFFFFF55 : 0xFFFFFFFF) : 0x80FFFFFF;
            g.outline(getX(i) - 1, getY(i) - 1, pw() + 2, ph(i) + 2, col);
        }
        String hint = "Drag a box to move it. Arrow keys nudge the highlighted one. Done to save.";
        g.text(font, hint, width / 2 - font.width(hint) / 2, 12, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0) {
            int which = inside(1, mx, my) ? 1 : (inside(0, mx, my) ? 0 : -1);
            if (which >= 0) {
                target = which;
                dragging = true;
                offX = (int) mx - getX(which);
                offY = (int) my - getY(which);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            setPos(target, (int) event.x() - offX, (int) event.y() - offY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int step = (event.modifiers() & 3) != 0 ? 10 : 1; // left/right shift in both GLFW and SDL masks
        int key = event.key();
        int x = getX(target), y = getY(target);
        if (key == Compat.KEY_LEFT) x -= step;
        else if (key == Compat.KEY_RIGHT) x += step;
        else if (key == Compat.KEY_UP) y -= step;
        else if (key == Compat.KEY_DOWN) y += step;
        else return super.keyPressed(event);
        setPos(target, x, y);
        return true;
    }

    @Override
    public void onClose() { Compat.openScreen(parent); }

    @Override
    public void removed() {
        AhsClient.menuOpen = false;
        AhsConfig.save();
    }
}
