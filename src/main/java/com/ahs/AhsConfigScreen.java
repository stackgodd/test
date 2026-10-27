package com.ahs;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** /ahs - every setting, three tabs. Changes apply instantly and save when the screen closes. */
public class AhsConfigScreen extends Screen {
    private record Label(int x, int y, String text) {}
    private record Swatch(int x, int y, IntSupplier color) {}

    private final Screen parent;
    private int page;
    private final List<Label> labels = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();

    public AhsConfigScreen(Screen parent, int page) {
        super(Component.literal("Auction House Timer"));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        AhsClient.menuOpen = true;
        labels.clear();
        swatches.clear();
        AhsConfig c = AhsConfig.INSTANCE;
        int cx = width / 2, lx = cx - 155, rx = cx + 5, w = 150, top = 36, row = 24;

        tab(cx - 155, "Auction", 0);
        tab(cx - 50, "Appearance", 1);
        tab(cx + 55, "Papering", 2);

        if (page == 0) {
            field(lx, top, "Duration", Fmt.compactDuration(c.durationMs), 12, s -> {
                long ms = Fmt.parseDuration(s);
                if (ms > 0) c.durationMs = ms;
            });
            field(rx, top, "Start bid", Fmt.money(c.startingBid), 14, s -> {
                double v = Fmt.parseAmount(s);
                if (!Double.isNaN(v) && v >= 0) c.startingBid = v;
            });
            cycle(lx, top + row, w, () -> "Item: " + pretty(c.itemSource),
                    () -> c.itemSource = next(c.itemSource, AhsConfig.ItemSource.values()));
            cycle(rx, top + row, w, () -> "Restart on key: " + yn(c.restartWhileRunning),
                    () -> c.restartWhileRunning = !c.restartWhileRunning);
            field(lx, top + row * 2, "Result (s)", String.valueOf(c.resultSeconds), 3, s -> {
                try { int v = Integer.parseInt(s.trim()); if (v > 0 && v < 600) c.resultSeconds = v; }
                catch (NumberFormatException ignored) {}
            });
            cycle(rx, top + row * 2, w, () -> "End sound: " + yn(c.playSound), () -> c.playSound = !c.playSound);
            cycle(lx, top + row * 3, w, () -> "Ignore my own bids: " + yn(c.ignoreOwnBids),
                    () -> c.ignoreOwnBids = !c.ignoreOwnBids);

            int ry = top + row * 4 + 6;
            labels.add(new Label(lx, ry, "Bid / payment chat pattern (regex with groups: player, amount)"));
            regexField(lx, ry + 11, c.bidRegex, s -> c.bidRegex = s);
            labels.add(new Label(lx, ry + 36, "Bids only count while a timer is running. Chat is read, never sent."));
        } else if (page == 1) {
            addRenderableWidget(new FloatSlider(lx, top, w, "Scale", 0.5f, 3f, c.scale, v -> c.scale = v));
            addRenderableWidget(new FloatSlider(rx, top, w, "Background", 0f, 1f, c.bgOpacity, v -> c.bgOpacity = v));
            cycle(lx, top + row, w, () -> "Timer: " + pretty(c.timerStyle),
                    () -> c.timerStyle = next(c.timerStyle, AhsConfig.TimerStyle.values()));
            cycle(rx, top + row, w, () -> "Bar: " + pretty(c.barStyle),
                    () -> c.barStyle = next(c.barStyle, AhsConfig.BarStyle.values()));
            cycle(lx, top + row * 2, w, () -> "Show bar: " + yn(c.showBar), () -> c.showBar = !c.showBar);
            cycle(rx, top + row * 2, w, () -> "Show item: " + yn(c.showItem), () -> c.showItem = !c.showItem);
            cycle(lx, top + row * 3, w, () -> "Show top bid: " + yn(c.showTopBid), () -> c.showTopBid = !c.showTopBid);
            field(rx, top + row * 3, "Low at (s)", String.valueOf(c.lowTimeSeconds), 3, s -> {
                try { int v = Integer.parseInt(s.trim()); if (v >= 0 && v < 600) c.lowTimeSeconds = v; }
                catch (NumberFormatException ignored) {}
            });
            colorField(lx, top + row * 4, "Text", () -> c.textColor, v -> c.textColor = v);
            colorField(rx, top + row * 4, "Bar", () -> c.barColor, v -> c.barColor = v);
            colorField(lx, top + row * 5, "Background", () -> c.bgColor, v -> c.bgColor = v);
            colorField(rx, top + row * 5, "Low time", () -> c.lowColor, v -> c.lowColor = v);
            labels.add(new Label(lx, top + row * 6 + 4, "Colours and scale also apply to the papering panel."));
        } else {
            cycle(lx, top, w, () -> "Papering: " + yn(c.papEnabled), () -> c.papEnabled = !c.papEnabled);
            cycle(rx, top, w, () -> "Tag chat payments: " + yn(c.papTagChat), () -> c.papTagChat = !c.papTagChat);
            field(lx, top + row, "Win pays x", BigDecimal.valueOf(c.papPayoutMultiplier).stripTrailingZeros().toPlainString(), 6, s -> {
                try { double v = Double.parseDouble(s.trim()); if (v > 0 && v < 1000) c.papPayoutMultiplier = v; }
                catch (NumberFormatException ignored) {}
            });
            cycle(rx, top + row, w, () -> "Pre-type /pay on win: " + yn(c.papPrefillPay),
                    () -> c.papPrefillPay = !c.papPrefillPay);
            field(lx, top + row * 2, "Recent rows", String.valueOf(c.papRecentRows), 2, s -> {
                try { int v = Integer.parseInt(s.trim()); if (v >= 0 && v <= 15) c.papRecentRows = v; }
                catch (NumberFormatException ignored) {}
            });

            int ry = top + row * 3 + 6;
            labels.add(new Label(lx, ry, "Payment pattern: who paid you and how much (groups: player, amount)"));
            regexField(lx, ry + 11, c.papPayRegex, s -> c.papPayRegex = s);
            int cy = ry + 38;
            labels.add(new Label(lx, cy, "Pay command  ({player} and {amount} are filled in for you)"));
            EditBox pc = new EditBox(font, lx, cy + 11, 310, 20, Component.literal("Pay command"));
            pc.setMaxLength(100);
            pc.setValue(c.papPayCommand);
            pc.setResponder(s -> c.papPayCommand = s);
            addRenderableWidget(pc);
            labels.add(new Label(lx, cy + 38, "Keys (rebind in Controls): Y = client wins, N = client loses. Tie = loss."));
        }

        addRenderableWidget(Button.builder(Component.literal("Move HUD"), b -> Compat.openScreen(new HudMoveScreen(this)))
                .bounds(lx, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx + 55, height - 28, 100, 20).build());
    }

    // ---- widget helpers ----

    private void tab(int x, String name, int index) {
        Button b = Button.builder(Component.literal(name), btn -> { page = index; rebuildWidgets(); })
                .bounds(x, 8, 100, 20).build();
        b.active = page != index;
        addRenderableWidget(b);
    }

    private void cycle(int x, int y, int w, Supplier<String> label, Runnable click) {
        addRenderableWidget(Button.builder(Component.literal(label.get()), b -> {
            click.run();
            b.setMessage(Component.literal(label.get()));
        }).bounds(x, y, w, 20).build());
    }

    private void field(int x, int y, String label, String init, int max, Consumer<String> onChange) {
        labels.add(new Label(x, y + 6, label));
        EditBox tf = new EditBox(font, x + 78, y, 72, 20, Component.literal(label));
        tf.setMaxLength(max);
        tf.setValue(init);
        tf.setResponder(onChange);
        addRenderableWidget(tf);
    }

    private void regexField(int x, int y, String init, Consumer<String> onChange) {
        EditBox rf = new EditBox(font, x, y, 310, 20, Component.literal("Pattern"));
        rf.setMaxLength(400);
        rf.setValue(init);
        rf.setResponder(s -> {
            onChange.accept(s);
            rf.setTextColor(BidParser.valid(s) ? 0xFFE0E0E0 : 0xFFFF5555);
        });
        addRenderableWidget(rf);
    }

    private void colorField(int x, int y, String label, IntSupplier get, IntConsumer set) {
        labels.add(new Label(x, y + 6, label));
        swatches.add(new Swatch(x + 62, y + 4, get));
        EditBox tf = new EditBox(font, x + 78, y, 72, 20, Component.literal(label));
        tf.setMaxLength(7);
        tf.setValue(String.format("#%06X", get.getAsInt()));
        tf.setResponder(s -> {
            String h = s.startsWith("#") ? s.substring(1) : s;
            if (h.matches("[0-9a-fA-F]{6}")) {
                set.accept(Integer.parseInt(h, 16));
                tf.setTextColor(0xFFE0E0E0);
            } else {
                tf.setTextColor(0xFFFF5555);
            }
        });
        addRenderableWidget(tf);
    }

    private static <E extends Enum<E>> E next(E cur, E[] all) { return all[(cur.ordinal() + 1) % all.length]; }

    private static String yn(boolean b) { return b ? "On" : "Off"; }

    private static String pretty(Enum<?> e) {
        String[] parts = e.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static class FloatSlider extends AbstractSliderButton {
        private final float min, max;
        private final String name;
        private final Consumer<Float> set;

        FloatSlider(int x, int y, int w, String name, float min, float max, float cur, Consumer<Float> set) {
            super(x, y, w, 20, Component.empty(), (cur - min) / (max - min));
            this.min = min; this.max = max; this.name = name; this.set = set;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(name + ": " + String.format(Locale.ROOT, "%.2f", min + (max - min) * value)));
        }

        @Override
        protected void applyValue() { set.accept((float) (min + (max - min) * value)); }
    }

    // ---- rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        for (Label l : labels) g.text(font, l.text(), l.x(), l.y(), 0xFFFFFFFF, true);
        for (Swatch s : swatches) {
            g.fill(s.x() - 1, s.y() - 1, s.x() + 11, s.y() + 13, 0xFFFFFFFF);
            g.fill(s.x(), s.y(), s.x() + 10, s.y() + 12, 0xFF000000 | s.color().getAsInt());
        }
        if (page == 1) {
            HudRenderer.draw(g, HudRenderer.demo(), width / 2 - HudRenderer.W / 2,
                    height - 28 - 8 - HudRenderer.H, 1f, AhsConfig.INSTANCE);
        }
    }

    @Override
    public void onClose() { Compat.openScreen(parent); }

    @Override
    public void removed() {
        AhsClient.menuOpen = false;
        AhsConfig.save();
    }
}
