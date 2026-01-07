package com.masuary.entitylibrary.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;

/**
 * Minimal GUI texture helpers for NeoForge 1.21.1.
 *
 * GuiGraphics#blit has multiple overloads in 1.21.x. Depending on mappings, the
 * U/V texture coordinates may be ints or floats, and some overloads include a Z
 * (blit offset) int. This helper picks the best available overload at runtime.
 */
final class GuiTextureUtil {

    private GuiTextureUtil() {
    }

    /**
     * Draws a 9-slice panel.
     *
     * @param cornerPx size of each corner in pixels (in the source texture)
     */
    static void nineSlice(
            final GuiGraphics gg,
            final ResourceLocation texture,
            final int x,
            final int y,
            final int w,
            final int h,
            final int texW,
            final int texH,
            final int cornerPx
    ) {
        if (w <= 0 || h <= 0) {
            return;
        }

        final int c = Math.max(0, cornerPx);
        if (c == 0 || w < c * 2 || h < c * 2) {
            // Fallback: simple stretch (still better than nothing on tiny windows)
            blit(gg, texture, x, y, 0, 0, w, h, texW, texH);
            return;
        }

        final int midDstW = Math.max(0, w - c * 2);
        final int midDstH = Math.max(0, h - c * 2);

        // Corners
        blit(gg, texture, x, y, 0, 0, c, c, texW, texH);
        blit(gg, texture, x + w - c, y, texW - c, 0, c, c, texW, texH);
        blit(gg, texture, x, y + h - c, 0, texH - c, c, c, texW, texH);
        blit(gg, texture, x + w - c, y + h - c, texW - c, texH - c, c, c, texW, texH);

        // Edges
        blit(gg, texture, x + c, y, c, 0, midDstW, c, texW, texH);
        blit(gg, texture, x + c, y + h - c, c, texH - c, midDstW, c, texW, texH);
        blit(gg, texture, x, y + c, 0, c, c, midDstH, texW, texH);
        blit(gg, texture, x + w - c, y + c, texW - c, c, c, midDstH, texW, texH);

        // Center
        blit(gg, texture, x + c, y + c, c, c, midDstW, midDstH, texW, texH);
    }

    // --- blit compat ---

    private static Method BLIT_RL_INT;     // (RL, x, y, u, v, w, h, texW, texH)
    private static Method BLIT_RL_FLOAT;   // (RL, x, y, uF, vF, w, h, texW, texH)
    private static Method BLIT_RL_Z_INT;   // (RL, x, y, z, u, v, w, h, texW, texH)
    private static Method BLIT_RL_Z_FLOAT; // (RL, x, y, z, uF, vF, w, h, texW, texH)

    /**
     * Draws a sub-rectangle from a texture to the screen.
     */
    static void blit(
            final GuiGraphics gg,
            final ResourceLocation texture,
            final int x,
            final int y,
            final int u,
            final int v,
            final int w,
            final int h,
            final int texW,
            final int texH
    ) {
        if (w <= 0 || h <= 0) {
            return;
        }

        // Prefer the plain int UV overload.
        try {
            final Method m = getBlitRlInt();
            if (m != null) {
                m.invoke(gg, texture, x, y, u, v, w, h, texW, texH);
                return;
            }
        } catch (Throwable ignored) {
        }

        // Next, try float UV.
        try {
            final Method m = getBlitRlFloat();
            if (m != null) {
                m.invoke(gg, texture, x, y, (float) u, (float) v, w, h, texW, texH);
                return;
            }
        } catch (Throwable ignored) {
        }

        // Some builds include a Z/blitOffset int.
        try {
            final Method m = getBlitRlZInt();
            if (m != null) {
                m.invoke(gg, texture, x, y, 0, u, v, w, h, texW, texH);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            final Method m = getBlitRlZFloat();
            if (m != null) {
                m.invoke(gg, texture, x, y, 0, (float) u, (float) v, w, h, texW, texH);
                return;
            }
        } catch (Throwable ignored) {
        }

        // Final fallback: solid fill so the UI remains usable even if signatures change.
        gg.fill(x, y, x + w, y + h, 0xAA000000);
    }

    private static Method getBlitRlInt() {
        if (BLIT_RL_INT != null) {
            return BLIT_RL_INT;
        }
        BLIT_RL_INT = findBlitExact(ResourceLocation.class,
                int.class, int.class,
                int.class, int.class,
                int.class, int.class,
                int.class, int.class);
        return BLIT_RL_INT;
    }

    private static Method getBlitRlFloat() {
        if (BLIT_RL_FLOAT != null) {
            return BLIT_RL_FLOAT;
        }
        BLIT_RL_FLOAT = findBlitExact(ResourceLocation.class,
                int.class, int.class,
                float.class, float.class,
                int.class, int.class,
                int.class, int.class);
        return BLIT_RL_FLOAT;
    }

    private static Method getBlitRlZInt() {
        if (BLIT_RL_Z_INT != null) {
            return BLIT_RL_Z_INT;
        }
        BLIT_RL_Z_INT = findBlitExact(ResourceLocation.class,
                int.class, int.class,
                int.class,
                int.class, int.class,
                int.class, int.class,
                int.class, int.class);
        return BLIT_RL_Z_INT;
    }

    private static Method getBlitRlZFloat() {
        if (BLIT_RL_Z_FLOAT != null) {
            return BLIT_RL_Z_FLOAT;
        }
        BLIT_RL_Z_FLOAT = findBlitExact(ResourceLocation.class,
                int.class, int.class,
                int.class,
                float.class, float.class,
                int.class, int.class,
                int.class, int.class);
        return BLIT_RL_Z_FLOAT;
    }

    private static Method findBlitExact(final Class<?>... params) {
        for (final Method m : GuiGraphics.class.getMethods()) {
            if (!m.getName().equals("blit")) {
                continue;
            }
            final Class<?>[] p = m.getParameterTypes();
            if (p.length != params.length) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < params.length; i++) {
                if (p[i] != params[i]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return m;
            }
        }
        return null;
    }
}
