package com.ahs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Papering helper. Payments to you become a queue of bets. After you flick the droppers you press
 * one key (client wins / client loses) and the mod logs it, tells you what to pay, and moves on.
 * Rules: client wins if their number is higher; a tie loses; a win pays payoutMultiplier x the bet.
 */
public final class PaperingState {
    private PaperingState() {}

    public record Bet(int id, String player, double amount, long time) {}
    public record Result(Bet bet, boolean win, double payout) {}

    public static final Deque<Bet> queue = new ArrayDeque<>();
    public static final List<Result> history = new ArrayList<>(); // newest first
    public static int nextId = 1, wins = 0, losses = 0;
    public static double net = 0, wagered = 0;

    private static Pattern cached;
    private static String cachedSrc;

    private static Pattern pattern(String src) {
        if (!src.equals(cachedSrc)) {
            cachedSrc = src;
            try { cached = Pattern.compile(src, Pattern.CASE_INSENSITIVE); }
            catch (PatternSyntaxException e) { cached = null; }
        }
        return cached;
    }

    /** Called for every incoming game message. Returns the message to show (tagged with a bet number if it was a bet). */
    public static Component onMessage(Component message) {
        AhsConfig c = AhsConfig.INSTANCE;
        if (!c.papEnabled) return message;
        String s = message.getString();
        if (s.startsWith("[PAP]") || s.startsWith("[AHS]")) return message;
        Pattern p = pattern(c.papPayRegex);
        if (p == null) return message;
        try {
            Matcher m = p.matcher(s);
            if (!m.find()) return message;
            String player = m.group("player");
            double amount = Fmt.parseAmount(m.group("amount"));
            if (Double.isNaN(amount) || amount <= 0) return message;
            if (player.equalsIgnoreCase(Minecraft.getInstance().getUser().getName())) return message;
            Bet bet = addBet(player, amount);
            if (!c.papTagChat) return message;
            return message.copy().append(
                    Component.literal(" [bet #" + bet.id() + "]").withStyle(ChatFormatting.GRAY));
        } catch (IllegalArgumentException e) {
            return message; // regex has no "player"/"amount" groups
        }
    }

    public static Bet addBet(String player, double amount) {
        Bet bet = new Bet(nextId++, player, amount, System.currentTimeMillis());
        queue.addLast(bet);
        return bet;
    }

    public static Bet current() { return queue.peekFirst(); }

    public static void resolve(boolean win) {
        Bet b = queue.pollFirst();
        if (b == null) {
            bar("No bet waiting");
            return;
        }
        AhsConfig c = AhsConfig.INSTANCE;
        double payout = win ? b.amount() * c.papPayoutMultiplier : 0;
        Result r = new Result(b, win, payout);
        history.add(0, r);
        while (history.size() > 200) history.remove(history.size() - 1);
        apply(r, 1);

        Component prefix = Component.literal("[PAP] ").withStyle(ChatFormatting.GOLD);
        Component head = Component.literal("#" + b.id() + " " + b.player() + " $" + Fmt.money(b.amount()) + ": ")
                .withStyle(ChatFormatting.WHITE);
        Component tail = win
                ? Component.literal("CLIENT WINS - pay $" + Fmt.money(payout)).withStyle(ChatFormatting.GREEN)
                : Component.literal("client loses - you keep $" + Fmt.money(b.amount())).withStyle(ChatFormatting.RED);
        Compat.chat(Component.empty().append(prefix).append(head).append(tail));

        if (win && c.papPrefillPay) {
            AhsClient.pendingChat = c.papPayCommand
                    .replace("{player}", b.player())
                    .replace("{amount}", plain(payout));
        }
        Bet next = queue.peekFirst();
        if (next != null) bar("Next: " + next.player() + " $" + Fmt.money(next.amount()));
    }

    public static void skip() {
        Bet b = queue.pollFirst();
        if (b == null) { bar("No bet waiting"); return; }
        bar("Skipped #" + b.id() + " " + b.player());
    }

    public static void undo() {
        if (history.isEmpty()) { bar("Nothing to undo"); return; }
        Result r = history.remove(0);
        apply(r, -1);
        queue.addFirst(r.bet());
        bar("Undid #" + r.bet().id() + " - it's the current bet again");
    }

    public static void clearQueue() {
        queue.clear();
        bar("Bet queue cleared");
    }

    public static void resetAll() {
        queue.clear();
        history.clear();
        wins = 0;
        losses = 0;
        net = 0;
        wagered = 0;
        nextId = 1;
        bar("Papering session reset");
    }

    private static void apply(Result r, int sign) {
        if (r.win()) wins += sign; else losses += sign;
        wagered += sign * r.bet().amount();
        net += sign * (r.bet().amount() - r.payout());
    }

    /** 100000 -> "100000" (no scientific notation, no trailing zeros) */
    public static String plain(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private static void bar(String s) {
        Compat.actionBar(Component.literal("[PAP] " + s).withStyle(ChatFormatting.GOLD));
    }
}
