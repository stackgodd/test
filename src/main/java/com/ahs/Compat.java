package com.ahs;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Minecraft 26.x keeps moving things between releases. Everything that has changed so far is looked
 * up by name here, so the rest of the mod stays the same:
 *  - opening screens (26.1: Minecraft#setScreen, 26.2+: setScreenAndShow / Gui#setScreen)
 *  - key codes and the KeyMapping constructor (26.3 switched from GLFW to SDL key codes)
 */
public final class Compat {
    private Compat() {}

    // Looked up at runtime on purpose, so they are not baked in at compile time.
    public static final int KEY_LEFT = keyCode("KEY_LEFT");
    public static final int KEY_RIGHT = keyCode("KEY_RIGHT");
    public static final int KEY_UP = keyCode("KEY_UP");
    public static final int KEY_DOWN = keyCode("KEY_DOWN");

    public static int keyCode(String field) {
        try {
            return InputConstants.class.getField(field).getInt(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    public static KeyMapping newKeyMapping(String id, String keyField, KeyMapping.Category category) {
        int code = keyCode(keyField);
        for (Constructor<?> c : KeyMapping.class.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            try {
                if (p.length == 4 && p[0] == String.class && p[1].isEnum() && p[2] == int.class) {
                    Object type = null;
                    for (Object o : p[1].getEnumConstants()) {
                        String n = ((Enum<?>) o).name();
                        if (n.equals("KEYSYM") || n.equals("KEYBOARD")) { type = o; break; }
                    }
                    if (type != null) return (KeyMapping) c.newInstance(id, type, code, category);
                } else if (p.length == 3 && p[0] == String.class && p[1] == int.class) {
                    return (KeyMapping) c.newInstance(id, code, category);
                }
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
        throw new IllegalStateException("[AHS] No usable KeyMapping constructor found on this Minecraft version");
    }

    /** Opens a screen (null closes the current one). */
    public static void openScreen(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (call(mc, "setScreenAndShow", screen)) return;
        if (call(mc, "setScreen", screen)) return;
        try {
            Object gui = Minecraft.class.getField("gui").get(mc);
            if (gui != null && call(gui, "setScreen", screen)) return;
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        System.err.println("[AHS] Could not open a screen on this Minecraft version");
    }

    /** Opens the chat box with text already typed in (you still press Enter yourself). */
    public static boolean openChat(String text) {
        try {
            Class<?> cs = Class.forName("net.minecraft.client.gui.screens.ChatScreen");
            for (Constructor<?> c : cs.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length >= 1 && p[0] == String.class) {
                    Object[] args = new Object[p.length];
                    args[0] = text;
                    for (int i = 1; i < p.length; i++) args[i] = p[i] == boolean.class ? Boolean.FALSE : null;
                    openScreen((Screen) c.newInstance(args));
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Action bar message, falling back to chat if the method moved. */
    public static void actionBar(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (Method m : mc.player.getClass().getMethods()) {
            if (m.getName().equals("displayClientMessage") && m.getParameterCount() == 2) {
                try {
                    m.invoke(mc.player, text, Boolean.TRUE);
                    return;
                } catch (ReflectiveOperationException ignored) {
                    break;
                }
            }
        }
        chat(text);
    }

    /** Local-only chat line. Never sent to the server. */
    public static void chat(Component text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(text);
    }

    private static boolean call(Object target, String name, Screen arg) {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(Screen.class)) {
                try {
                    m.invoke(target, new Object[] { arg });
                } catch (ReflectiveOperationException e) {
                    e.printStackTrace();
                }
                return true;
            }
        }
        return false;
    }
}
