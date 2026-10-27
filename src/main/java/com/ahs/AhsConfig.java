package com.ahs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Every setting lives here and is editable from /ahs. Saved to config/auction-house-timer.json */
public class AhsConfig {
    public enum ItemSource { AUTO, HELD, LOOKED_AT }
    public enum TimerStyle { NUMBERS, CIRCLE, BAR_ONLY }
    public enum BarStyle { SOLID, SEGMENTED, GRADIENT }

    // --- auction ---
    public long durationMs = 30_000;
    public double startingBid = 0;
    public ItemSource itemSource = ItemSource.AUTO;
    public boolean restartWhileRunning = false;
    public int resultSeconds = 8;
    public boolean playSound = true;
    public boolean ignoreOwnBids = false;
    /** Needs named groups "player" and "amount". Tune this to DonutSMP's real bid/payment message. */
    public String bidRegex =
            "(?<player>\\w{3,16})[>:\\]]*\\s+(?:paid you|sent you|bids?|offers?|offered)\\D{0,15}?(?<amount>\\d[\\d,]*(?:\\.\\d+)?[kmb]?)\\b";

    // --- papering ---
    public boolean papEnabled = false;
    public boolean papTagChat = true;
    public boolean papPrefillPay = true;
    public double papPayoutMultiplier = 2.0;
    public int papRecentRows = 5;
    public String papPayCommand = "/pay {player} {amount}";
    /** Payments from players = bets. Needs named groups "player" and "amount". */
    public String papPayRegex =
            "(?<player>\\w{3,16})\\s+(?:paid you|sent you)\\D{0,15}?(?<amount>\\d[\\d,]*(?:\\.\\d+)?[kmbt]?)\\b";
    public int papX = 10, papY = 70;

    // --- HUD (shared look) ---
    public int hudX = 10, hudY = 10;
    public float scale = 1.0f;
    public float bgOpacity = 0.6f;
    public TimerStyle timerStyle = TimerStyle.NUMBERS;
    public BarStyle barStyle = BarStyle.SOLID;
    public boolean showBar = true, showItem = true, showTopBid = true;
    public int textColor = 0xFFFFFF, barColor = 0x55FF55, bgColor = 0x101018, lowColor = 0xFF5555;
    public int lowTimeSeconds = 5;

    // --- persistence ---
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("auction-house-timer.json");
    public static AhsConfig INSTANCE = new AhsConfig();

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH)) {
                    AhsConfig loaded = GSON.fromJson(r, AhsConfig.class);
                    if (loaded != null) INSTANCE = loaded;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            System.err.println("[AHS] Could not read config, using defaults: " + e);
        }
        if (INSTANCE.itemSource == null) INSTANCE.itemSource = ItemSource.AUTO;
        if (INSTANCE.timerStyle == null) INSTANCE.timerStyle = TimerStyle.NUMBERS;
        if (INSTANCE.barStyle == null) INSTANCE.barStyle = BarStyle.SOLID;
        if (INSTANCE.papPayCommand == null) INSTANCE.papPayCommand = "/pay {player} {amount}";
        if (INSTANCE.papPayRegex == null) INSTANCE.papPayRegex = new AhsConfig().papPayRegex;
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException e) {
            System.err.println("[AHS] Could not save config: " + e);
        }
    }
}
