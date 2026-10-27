package com.ahs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The running auction. The item is COPIED when the timer starts, so swapping
 * hotbar slots or looking away afterwards changes nothing.
 */
public final class AuctionState {
    private AuctionState() {}

    public enum Phase { IDLE, RUNNING, RESULT }

    public static Phase phase = Phase.IDLE;
    public static ItemStack stack = ItemStack.EMPTY;
    public static Component itemName = Component.empty();
    public static long totalMs, endAtMs, resultUntilMs;
    public static String topBidder = null;
    public static double topBid = 0;

    public static void startOrRestart(Minecraft mc) {
        if (mc.player == null) return;
        AhsConfig c = AhsConfig.INSTANCE;
        if (phase == Phase.RUNNING && !c.restartWhileRunning) {
            bar("Auction already running (stop it first)");
            return;
        }
        ItemStack picked = pick(mc, c.itemSource);
        if (picked.isEmpty()) {
            bar("Hold or look at an item first");
            return;
        }
        stack = picked.copy();
        itemName = stack.getHoverName();
        totalMs = Math.max(1000, c.durationMs);
        endAtMs = System.currentTimeMillis() + totalMs;
        topBidder = null;
        topBid = 0;
        phase = Phase.RUNNING;
        bar("Auction started: " + itemName.getString() + " (" + Fmt.compactDuration(totalMs) + ")");
    }

    public static void stop() {
        if (phase == Phase.IDLE) return;
        phase = Phase.IDLE;
        bar("Auction stopped");
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null) { phase = Phase.IDLE; return; }
        long now = System.currentTimeMillis();
        if (phase == Phase.RUNNING && now >= endAtMs) finish(mc, now);
        else if (phase == Phase.RESULT && now >= resultUntilMs) phase = Phase.IDLE;
    }

    public static void onBid(String player, double amount) {
        if (phase != Phase.RUNNING) return;
        if (amount < AhsConfig.INSTANCE.startingBid) return;
        if (amount > topBid) {
            topBid = amount;
            topBidder = player;
        }
    }

    private static void finish(Minecraft mc, long now) {
        phase = Phase.RESULT;
        resultUntilMs = now + Math.max(1, AhsConfig.INSTANCE.resultSeconds) * 1000L;
        if (mc.player == null) return;

        Component prefix = Component.literal("[AHS] ").withStyle(ChatFormatting.GOLD);
        Component msg;
        if (topBidder == null) {
            msg = Component.empty().append(prefix)
                    .append(Component.literal("Ended: ").withStyle(ChatFormatting.WHITE))
                    .append(itemName)
                    .append(Component.literal(" - no bids.").withStyle(ChatFormatting.GRAY));
        } else {
            msg = Component.empty().append(prefix)
                    .append(Component.literal("SOLD ").withStyle(ChatFormatting.GREEN))
                    .append(itemName)
                    .append(Component.literal(" to ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(topBidder).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" for $" + Fmt.money(topBid)).withStyle(ChatFormatting.GREEN));
        }
        Compat.chat(msg); // local only, never sent to the server
        if (AhsConfig.INSTANCE.playSound) mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1f, 1f);
    }

    public static ItemStack pick(Minecraft mc, AhsConfig.ItemSource src) {
        ItemStack held = mc.player.getMainHandItem();
        ItemStack looked = lookedAt(mc);
        return switch (src) {
            case HELD -> held;
            case LOOKED_AT -> looked;
            case AUTO -> !looked.isEmpty() ? looked : held;
        };
    }

    private static ItemStack lookedAt(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e instanceof ItemEntity ie) return ie.getItem();
            if (e instanceof ItemFrame f) return f.getItem();
        }
        return ItemStack.EMPTY;
    }

    private static void bar(String s) {
        Compat.actionBar(Component.literal("[AHS] " + s).withStyle(ChatFormatting.GOLD));
    }
}
