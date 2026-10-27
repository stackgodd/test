package com.ahs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Draws the auction panel. Used by the live HUD, the config preview and the move screen. */
public final class HudRenderer {
    private HudRenderer() {}

    public static final int W = 190, H = 52;

    public record Model(ItemStack stack, Component name, double fraction, long remainingMs,
                        String bidder, double bid, boolean result) {}

    public static Model live() {
        long now = System.currentTimeMillis();
        switch (AuctionState.phase) {
            case RUNNING -> {
                long rem = Math.max(0, AuctionState.endAtMs - now);
                return new Model(AuctionState.stack, AuctionState.itemName,
                        (double) rem / AuctionState.totalMs, rem, AuctionState.topBidder, AuctionState.topBid, false);
            }
            case RESULT -> {
                return new Model(AuctionState.stack, AuctionState.itemName, 0, 0,
                        AuctionState.topBidder, AuctionState.topBid, true);
            }
            default -> { return null; }
        }
    }

    private static ItemStack demoStack;

    public static Model demo() {
        if (demoStack == null) demoStack = new ItemStack(Items.DIAMOND_SWORD);
        long cycle = 20_000;
        long rem = cycle - (System.currentTimeMillis() % cycle);
        return new Model(demoStack, demoStack.getHoverName(), (double) rem / cycle, rem, "Steve", 1_250_000, false);
    }

    /** Draws at the configured position, clamped on screen. */
    public static void drawAtConfig(GuiGraphicsExtractor ctx, Model m) {
        AhsConfig c = AhsConfig.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        int w = (int) (W * c.scale), h = (int) (H * c.scale);
        int x = Math.max(0, Math.min(c.hudX, mc.getWindow().getGuiScaledWidth() - w));
        int y = Math.max(0, Math.min(c.hudY, mc.getWindow().getGuiScaledHeight() - h));
        draw(ctx, m, x, y, c.scale, c);
    }

    public static void draw(GuiGraphicsExtractor ctx, Model m, int x, int y, float scale, AhsConfig c) {
        Font tr = Minecraft.getInstance().font;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(scale, scale);

        boolean sold = m.bidder() != null;
        boolean low = !m.result() && m.remainingMs() <= c.lowTimeSeconds * 1000L;
        int accent = m.result() ? (sold ? 0x55FF55 : 0xAAAAAA) : (low ? c.lowColor : c.barColor);
        int textCol = 0xFF000000 | (low ? c.lowColor : c.textColor);
        double frac = m.result() ? 1.0 : m.fraction();

        // background + border
        int bgAlpha = Math.round(Math.max(0, Math.min(1, c.bgOpacity)) * 255) << 24;
        ctx.fill(0, 0, W, H, bgAlpha | c.bgColor);
        int border = 0xFF000000 | accent;
        ctx.fill(0, 0, W, 1, border);
        ctx.fill(0, H - 1, W, H, border);
        ctx.fill(0, 0, 1, H, border);
        ctx.fill(W - 1, 0, W, H, border);

        boolean zone = c.timerStyle != AhsConfig.TimerStyle.BAR_ONLY;
        int textX = c.showItem ? 28 : 6;
        int nameW = (zone ? W - 62 : W - 6) - textX;

        if (c.showItem) ctx.item(m.stack(), 6, 12);

        int nameY = c.showTopBid ? 11 : 17;
        FormattedCharSequence nameSeq = Language.getInstance().getVisualOrder(tr.substrByWidth(m.name(), nameW));
        ctx.text(tr, nameSeq, textX, nameY, 0xFFFFFFFF, true);

        if (c.showTopBid) {
            Component line;
            if (m.bidder() == null) {
                String s = m.result() ? "No bids"
                        : (c.startingBid > 0 ? "No bids - min $" + Fmt.money(c.startingBid) : "No bids yet");
                line = Component.literal(s).withStyle(ChatFormatting.GRAY);
            } else {
                line = Component.literal(m.result() ? "Winner: " : "Top: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(m.bidder()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("  $" + Fmt.money(m.bid())).withStyle(ChatFormatting.GREEN));
            }
            FormattedCharSequence lineSeq = Language.getInstance().getVisualOrder(tr.substrByWidth(line, nameW));
            ctx.text(tr, lineSeq, textX, 24, 0xFFFFFFFF, true);
        }

        // timer zone (right side)
        int zx = W - 32, zy = 20;
        if (c.timerStyle == AhsConfig.TimerStyle.NUMBERS) {
            String s;
            float ts = 1.5f;
            int col = textCol;
            if (m.result()) {
                s = sold ? "SOLD" : "NO BIDS";
                col = 0xFF000000 | accent;
                if (!sold) ts = 1f;
            } else {
                s = Fmt.clock(m.remainingMs(), true);
            }
            ctx.pose().pushMatrix();
            ctx.pose().translate((float) zx, (float) zy);
            ctx.pose().scale(ts, ts);
            ctx.text(tr, s, -tr.width(s) / 2, -4, col, true);
            ctx.pose().popMatrix();
        } else if (c.timerStyle == AhsConfig.TimerStyle.CIRCLE) {
            ring(ctx, zx, zy, 17, 4, frac, 0xFF000000 | accent, 0x55000000 | (c.textColor & 0xFFFFFF));
            String s = m.result() ? (sold ? "SOLD" : "END") : Fmt.clock(m.remainingMs(), false);
            float ts = s.length() > 3 ? 0.8f : 1f;
            ctx.pose().pushMatrix();
            ctx.pose().translate((float) zx, (float) zy);
            ctx.pose().scale(ts, ts);
            ctx.text(tr, s, -tr.width(s) / 2, -4, m.result() ? 0xFF000000 | accent : textCol, true);
            ctx.pose().popMatrix();
        }

        // shrinking bar
        if (c.showBar || c.timerStyle == AhsConfig.TimerStyle.BAR_ONLY) {
            int bh = c.timerStyle == AhsConfig.TimerStyle.BAR_ONLY ? 8 : 5;
            int bx = 6, bw = W - 12, by = H - 6 - bh;
            ctx.fill(bx, by, bx + bw, by + bh, 0x90000000);
            switch (c.barStyle) {
                case SOLID -> ctx.fill(bx, by, bx + (int) (bw * frac), by + bh, 0xFF000000 | accent);
                case GRADIENT -> {
                    int col = m.result() ? accent : lerp(c.lowColor, c.barColor, frac);
                    ctx.fill(bx, by, bx + (int) (bw * frac), by + bh, 0xFF000000 | col);
                }
                case SEGMENTED -> {
                    int n = 15;
                    double segW = (bw - (n - 1)) / (double) n;
                    int lit = (int) Math.ceil(frac * n);
                    for (int i = 0; i < lit; i++) {
                        int sx = bx + (int) Math.round(i * (segW + 1));
                        ctx.fill(sx, by, sx + Math.max(1, (int) segW), by + bh, 0xFF000000 | accent);
                    }
                }
            }
        }
        ctx.pose().popMatrix();
    }

    /** Clock-style ring: the lit arc starts at 12 o'clock and shrinks as time runs out. */
    private static void ring(GuiGraphicsExtractor ctx, int cx, int cy, int r, int thick, double frac, int on, int off) {
        double inner = r - thick, sweep = frac * Math.PI * 2;
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                double px = dx + 0.5, py = dy + 0.5;
                double d = Math.sqrt(px * px + py * py);
                if (d > r || d < inner) continue;
                double ang = Math.atan2(px, -py);
                if (ang < 0) ang += Math.PI * 2;
                ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, ang <= sweep ? on : off);
            }
        }
    }

    private static int lerp(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
