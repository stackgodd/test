package com.ahs;

import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Reads incoming chat/system messages and feeds matching bids into the auction. Read-only. */
public final class BidParser {
    private BidParser() {}

    private static Pattern cached;
    private static String cachedSrc;

    public static boolean valid(String src) {
        try { Pattern.compile(src); return true; } catch (PatternSyntaxException e) { return false; }
    }

    private static Pattern pattern(String src) {
        if (!src.equals(cachedSrc)) {
            cachedSrc = src;
            try { cached = Pattern.compile(src, Pattern.CASE_INSENSITIVE); }
            catch (PatternSyntaxException e) { cached = null; }
        }
        return cached;
    }

    public static void handle(String message, Minecraft mc) {
        if (AuctionState.phase != AuctionState.Phase.RUNNING || message == null) return;
        if (message.startsWith("[AHS]")) return;
        AhsConfig c = AhsConfig.INSTANCE;
        Pattern p = pattern(c.bidRegex);
        if (p == null) return;
        try {
            Matcher m = p.matcher(message);
            if (!m.find()) return;
            String player = m.group("player");
            double amount = Fmt.parseAmount(m.group("amount"));
            if (Double.isNaN(amount)) return;
            if (c.ignoreOwnBids && player.equalsIgnoreCase(mc.getUser().getName())) return;
            AuctionState.onBid(player, amount);
        } catch (IllegalArgumentException ignored) {
            // regex has no "player"/"amount" groups
        }
    }
}
