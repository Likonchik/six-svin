package ru.levin.util.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;

// Обложка трека: качаем байты off-thread (через MusicProxy -> SOCKS как метаданные), декодим в NativeImage,
// а создание DynamicTexture + регистрацию/освобождение в TextureManager делаем СТРОГО на render-потоке
// (mc.execute) — GL нельзя из чужого потока. Старую текстуру освобождаем (release) при смене -> нет VRAM-течи.
public final class AlbumArtCache {

    private volatile String currentUrl = "";
    private volatile ResourceLocation currentLoc = null;
    private final AtomicInteger counter = new AtomicInteger();

    public ResourceLocation get() { return currentLoc; }

    // вызывается с render-потока (MediaPlayer.tickAlways). Качает только если url новый.
    public void ensure(String url) {
        if (url == null || url.isBlank()) return;
        if (url.equals(currentUrl)) return;
        currentUrl = url;
        final String u = url;
        Thread t = new Thread(() -> {
            try {
                System.out.println("[MediaPlayer] art fetch: " + u);
                byte[] bytes = MusicProxy.httpBytes(u);
                if (bytes == null) { System.err.println("[MediaPlayer] art fetch FAILED (no bytes)"); return; }
                if (!u.equals(currentUrl)) return; // трек сменился, пока качали
                final NativeImage img;
                try {
                    img = decode(bytes); // ImageIO -> JPEG/PNG (NativeImage.read берёт только PNG)
                } catch (Throwable decodeErr) {
                    System.err.println("[MediaPlayer] art decode FAILED: " + decodeErr);
                    return;
                }
                if (img == null) { System.err.println("[MediaPlayer] art decode null"); return; }
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    try {
                        if (!u.equals(currentUrl)) { img.close(); return; }
                        DynamicTexture dyn = new DynamicTexture(img);
                        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("exosware", "music/cover_" + counter.incrementAndGet());
                        mc.getTextureManager().register(loc, dyn);
                        ResourceLocation old = currentLoc;
                        currentLoc = loc;
                        System.out.println("[MediaPlayer] art ready: " + loc);
                        if (old != null) {
                            try { mc.getTextureManager().release(old); } catch (Throwable ignored) {}
                        }
                    } catch (Throwable uploadErr) {
                        try { img.close(); } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable ignored) {}
        }, "OneTap-AlbumArt");
        t.setDaemon(true);
        t.start();
    }

    // JPEG/PNG -> NativeImage (через ImageIO; NativeImage.read поддерживает только PNG). ARGB -> ABGR (формат NativeImage).
    private static NativeImage decode(byte[] bytes) throws Exception {
        java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
        if (bi == null) return null;
        int w = bi.getWidth(), h = bi.getHeight();
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int argb = bi.getRGB(xx, yy);
                int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                img.setPixelRGBA(xx, yy, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return img;
    }

    // освобождаем текстуру при выключении/стопе
    public void dispose() {
        currentUrl = "";
        final ResourceLocation old = currentLoc;
        currentLoc = null;
        if (old != null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> { try { mc.getTextureManager().release(old); } catch (Throwable ignored) {} });
            } catch (Throwable ignored) {}
        }
    }
}
