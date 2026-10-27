package com.ahs;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class AhsClient implements ClientModInitializer {
    public static KeyMapping startKey, stopKey, papWinKey, papLoseKey;
    /** True while one of our own screens is open (they draw their own preview of the panels). */
    public static boolean menuOpen = false;
    /** Screens are opened on the next tick, otherwise the chat screen closing right after a command cancels them. */
    private static Screen pending;
    /** Text to pre-type into the chat box on the next tick (you still press Enter yourself). */
    public static String pendingChat;

    @Override
    public void onInitializeClient() {
        AhsConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ahs", "main"));
        startKey = KeyMappingHelper.registerKeyMapping(Compat.newKeyMapping("key.ahs.start", "KEY_K", category));
        stopKey = KeyMappingHelper.registerKeyMapping(Compat.newKeyMapping("key.ahs.stop", "KEY_J", category));
        papWinKey = KeyMappingHelper.registerKeyMapping(Compat.newKeyMapping("key.ahs.pap_win", "KEY_Y", category));
        papLoseKey = KeyMappingHelper.registerKeyMapping(Compat.newKeyMapping("key.ahs.pap_lose", "KEY_N", category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pending != null) {
                Screen s = pending;
                pending = null;
                Compat.openScreen(s);
            }
            if (pendingChat != null) {
                String text = pendingChat;
                pendingChat = null;
                if (!Compat.openChat(text)) {
                    Compat.chat(Component.literal("[PAP] Type: " + text).withStyle(ChatFormatting.GOLD));
                }
            }
            AuctionState.tick(mc);
            while (startKey.consumeClick()) AuctionState.startOrRestart(mc);
            while (stopKey.consumeClick()) AuctionState.stop();
            while (papWinKey.consumeClick()) PaperingState.resolve(true);
            while (papLoseKey.consumeClick()) PaperingState.resolve(false);
        });

        // Payments to you become bets. This can add a [bet #n] tag to the message you see; it never sends anything.
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
                overlay ? message : PaperingState.onMessage(message));

        // Auction bids: read-only listeners on incoming messages.
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (!overlay) BidParser.handle(msg.getString(), Minecraft.getInstance());
        });
        ClientReceiveMessageEvents.CHAT.register((msg, signed, sender, params, ts) ->
                BidParser.handle(msg.getString(), Minecraft.getInstance()));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("ahs", "auction_timer"), (graphics, tickCounter) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.options.hideGui || menuOpen) return;
                    HudRenderer.Model m = HudRenderer.live();
                    if (m != null) HudRenderer.drawAtConfig(graphics, m);
                    if (AhsConfig.INSTANCE.papEnabled) PaperingHud.drawAtConfig(graphics, PaperingHud.live());
                });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("timer")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal("[AHS] Timer length: "
                                + Fmt.compactDuration(AhsConfig.INSTANCE.durationMs)
                                + ". Use /timer 30s to change it."));
                        return 1;
                    })
                    .then(ClientCommands.argument("duration", StringArgumentType.greedyString()).executes(ctx -> {
                        long ms = Fmt.parseDuration(StringArgumentType.getString(ctx, "duration"));
                        if (ms <= 0) {
                            ctx.getSource().sendError(Component.literal("[AHS] Couldn't read that. Try 30s, 2m or 1m30s"));
                            return 0;
                        }
                        AhsConfig.INSTANCE.durationMs = ms;
                        AhsConfig.save();
                        ctx.getSource().sendFeedback(Component.literal("[AHS] Timer set to "
                                + Fmt.compactDuration(ms) + ". Press "
                                + startKey.getTranslatedKeyMessage().getString() + " to start."));
                        return 1;
                    })));

            dispatcher.register(ClientCommands.literal("ahs")
                    .executes(ctx -> { pending = new AhsConfigScreen(null, 0); return 1; })
                    .then(ClientCommands.literal("start").executes(ctx -> {
                        AuctionState.startOrRestart(Minecraft.getInstance());
                        return 1;
                    }))
                    .then(ClientCommands.literal("stop").executes(ctx -> {
                        AuctionState.stop();
                        return 1;
                    }))
                    .then(ClientCommands.literal("move").executes(ctx -> { pending = new HudMoveScreen(null); return 1; })));

            dispatcher.register(ClientCommands.literal("pap")
                    .executes(ctx -> { status(); return 1; })
                    .then(ClientCommands.literal("on").executes(ctx -> { setPap(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(ctx -> { setPap(false); return 1; }))
                    .then(ClientCommands.literal("win").executes(ctx -> { PaperingState.resolve(true); return 1; }))
                    .then(ClientCommands.literal("lose").executes(ctx -> { PaperingState.resolve(false); return 1; }))
                    .then(ClientCommands.literal("skip").executes(ctx -> { PaperingState.skip(); return 1; }))
                    .then(ClientCommands.literal("undo").executes(ctx -> { PaperingState.undo(); return 1; }))
                    .then(ClientCommands.literal("clear").executes(ctx -> { PaperingState.clearQueue(); return 1; }))
                    .then(ClientCommands.literal("reset").executes(ctx -> { PaperingState.resetAll(); return 1; }))
                    .then(ClientCommands.literal("settings").executes(ctx -> { pending = new AhsConfigScreen(null, 2); return 1; }))
                    .then(ClientCommands.literal("add")
                            .then(ClientCommands.argument("player", StringArgumentType.word())
                                    .then(ClientCommands.argument("amount", StringArgumentType.word()).executes(ctx -> {
                                        double amt = Fmt.parseAmount(StringArgumentType.getString(ctx, "amount"));
                                        if (Double.isNaN(amt) || amt <= 0) {
                                            ctx.getSource().sendError(Component.literal("[PAP] Couldn't read that amount. Try 50k or 1.5m"));
                                            return 0;
                                        }
                                        PaperingState.Bet b = PaperingState.addBet(StringArgumentType.getString(ctx, "player"), amt);
                                        Compat.chat(Component.literal("[PAP] Added bet #" + b.id() + " " + b.player()
                                                + " $" + Fmt.money(b.amount())).withStyle(ChatFormatting.GOLD));
                                        return 1;
                                    })))));
        });
    }

    private static void setPap(boolean on) {
        AhsConfig.INSTANCE.papEnabled = on;
        AhsConfig.save();
        Compat.chat(Component.literal("[PAP] Papering " + (on ? "ON - payments to you now become bets. "
                + papWinKey.getTranslatedKeyMessage().getString() + " = client wins, "
                + papLoseKey.getTranslatedKeyMessage().getString() + " = client loses"
                : "OFF")).withStyle(ChatFormatting.GOLD));
    }

    private static void status() {
        AhsConfig c = AhsConfig.INSTANCE;
        Compat.chat(Component.literal("[PAP] " + (c.papEnabled ? "ON" : "OFF") + " | queue " + PaperingState.queue.size()
                + " | W " + PaperingState.wins + " L " + PaperingState.losses
                + " | net " + (PaperingState.net >= 0 ? "+" : "-") + "$" + Fmt.money(Math.abs(PaperingState.net))
                + " | wagered $" + Fmt.money(PaperingState.wagered)).withStyle(ChatFormatting.GOLD));
    }
}
