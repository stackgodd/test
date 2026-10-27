package com.ahs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The papering panel: next bet, queue, session profit and the last few results. */
public final class PaperingHud {
    private PaperingHud() {}

    public static final int W = HudRenderer.W;

    public record Model(PaperingState.Bet next, int queued, List<PaperingState.Result> recent,
                        double net, int wins, int losses, double mult) {}

    public static Model live() {
        AhsConfig c = AhsConfig.INSTANCE;
        PaperingState.Bet next = PaperingState.current();
        int queued = Math.max(0, PaperingState.queue.size() - 1);
        return new Model(next, queued, new ArrayList<>(PaperingState.history), PaperingState.net,
                PaperingState.wins, PaperingState.losses, c.papPayoutMultiplier);
    }

    /** Fake data so you can style and place the panel before any real bets exist. */
    public static Model demo() {
        AhsConfig c = AhsConfig.INSTANCE;
        List<PaperingState.Result> recent = new ArrayList<>();
        recent.add(new PaperingState.Result(new PaperingState.Bet(3, "Cara", 5_000, 0), false, 0));
        recent.add(new PaperingState.Result(new PaperingState.Bet(2, "Bob", 100_000, 0), false, 0));
        recent.add(new PaperingState.Result(new PaperingState.Bet(1, "Alex", 20_000, 0), true, 20_000 * c.papPayoutMultiplier));
        return new Model(new PaperingState.Bet(4, "Steve", 50_000, 0), 2, recent, 85_000, 1, 2, c.papPayoutMultiplier);
    }

    public static boolean isEmpty() {
        return PaperingState.queue.isEmpty() && PaperingState.history.isEmpty();
    }

    public static int height(Model m, AhsConfig c) {
        int rows = Math.min(Math.max(0, c.papRecentRows), m.recent().size());
        return 54 + (rows > 0 ? rows * 10 + 4 : 0);
    }

    public static void drawAtConfig(GuiGraphicsExtractor ctx, Model m) {
        AhsConfig c = AhsConfig.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        int w = (int) (W * c.scale), h = (int) (height(m, c) * c.scale);
        int x = Math.max(0, Math.min(c.papX, mc.getWindow().getGuiScaledWidth() - w));
        int y = Math.max(0, Math.min(c.papY, mc.getWindow().getGuiScaledHeight() - h));
        draw(ctx, m, x, y, c.scale, c);
    }

    public static void draw(GuiGraphicsExtractor ctx, Model m, int x, int y, float scale, AhsConfig c) {
        Font tr = Minecraft.getInstance().font;
        int h = height(m, c);
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(scale, scale);

        int bgAlpha = Math.round(Math.max(0, Math.min(1, c.bgOpacity)) * 255) << 24;
        ctx.fill(0, 0, W, h, bgAlpha | c.bgColor);
        int border = 0xFF000000 | c.barColor;
        ctx.fill(0, 0, W, 1, border);
        ctx.fill(0, h - 1, W, h, border);
        ctx.fill(0, 0, 1, h, border);
        ctx.fill(W - 1, 0, W, h, border);

        // header + session profit
        ctx.text(tr, Component.literal("PAPERING").withStyle(ChatFormatting.BOLD), 6, 5, 0xFF000000 | c.textColor, true);
        boolean up = m.net() >= 0;
        Component netC = Component.literal("Net " + (up ? "+" : "-") + "$" + Fmt.money(Math.abs(m.net())))
                .withStyle(up ? ChatFormatting.GREEN : ChatFormatting.RED);
        ctx.text(tr, netC, W - 6 - tr.width(netC), 5, 0xFFFFFFFF, true);

        // next bet
        Component next;
        Component payLine;
        if (m.next() == null) {
            next = Component.literal("Waiting for payments...").withStyle(ChatFormatting.GRAY);
            payLine = Component.empty();
        } else {
            next = Component.literal("Next: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(m.next().player()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("  $" + Fmt.money(m.next().amount())).withStyle(ChatFormatting.GREEN));
            payLine = Component.literal("Win pays $" + Fmt.money(m.next().amount() * m.mult()) + "  (tie loses)")
                    .withStyle(ChatFormatting.GRAY);
        }
        ctx.text(tr, Language.getInstance().getVisualOrder(tr.substrByWidth(next, W - 12)), 6, 17, 0xFFFFFFFF, true);
        ctx.text(tr, Language.getInstance().getVisualOrder(tr.substrByWidth(payLine, W - 12)), 6, 28, 0xFFFFFFFF, true);

        // queue + win/loss counts
        Component stats = Component.literal("+" + m.queued() + " queued   ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("W " + m.wins()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("   "))
                .append(Component.literal("L " + m.losses()).withStyle(ChatFormatting.RED));
        ctx.text(tr, stats, 6, 40, 0xFFFFFFFF, true);

        // recent results (newest first)
        int rows = Math.min(Math.max(0, c.papRecentRows), m.recent().size());
        if (rows > 0) {
            ctx.fill(6, 51, W - 6, 52, 0x40FFFFFF);
            for (int i = 0; i < rows; i++) {
                PaperingState.Result r = m.recent().get(i);
                Component line = Component.literal(r.win() ? "WIN  " : "LOSS ")
                        .withStyle(r.win() ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .append(Component.literal(r.bet().player()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" $" + Fmt.money(r.bet().amount())).withStyle(ChatFormatting.GRAY));
                if (r.win()) {
                    line = line.copy().append(Component.literal(" -> $" + Fmt.money(r.payout())).withStyle(ChatFormatting.GREEN));
                }
                ctx.text(tr, Language.getInstance().getVisualOrder(tr.substrByWidth(line, W - 12)), 6, 55 + i * 10, 0xFFFFFFFF, true);
            }
        }
        ctx.pose().popMatrix();
    }
}
