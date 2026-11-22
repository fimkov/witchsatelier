package com.fimkov.witchsatelier.client.dev;

import com.fimkov.witchsatelier.WitchsAtelier;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

final class FilmRecorder {
    static final int FPS = 30;
    private static final List<Process> ENCODING = new ArrayList<>();

    private final File file;
    private Process ffmpeg;
    private OutputStream out;
    private long start;
    private long written;
    private int w, h;
    private byte[] buf;

    private FilmRecorder(File file) {
        this.file = file;
    }

    static FilmRecorder start(File file) {
        file.getParentFile().mkdirs();
        return new FilmRecorder(file);
    }

    void frame() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (ffmpeg == null) start = now;
        long due = (now - start) * FPS / 1_000_000_000L + 1;
        if (ffmpeg != null && written >= due) return;
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            if (ffmpeg == null) open(img.getWidth(), img.getHeight());
            if (img.getWidth() != w || img.getHeight() != h) return;
            int[] px = img.getPixelsRGBA();
            ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(px);
            while (written < due) {
                out.write(buf);
                written++;
            }
        } catch (Exception e) {
            WitchsAtelier.LOGGER.warn("[visualcheck] film {}: {}", file.getName(), e.toString());
            close();
        }
    }

    private void open(int width, int height) throws IOException {
        w = width;
        h = height;
        buf = new byte[w * h * 4];
        ffmpeg = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", w + "x" + h,
                "-r", String.valueOf(FPS), "-i", "-", "-vf", "scale=1280:-2", "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart", file.getAbsolutePath())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        out = new BufferedOutputStream(ffmpeg.getOutputStream(), 1 << 22);
    }

    void close() {
        if (ffmpeg == null) return;
        try {
            out.close();
        } catch (Exception ignored) {
        }
        synchronized (ENCODING) {
            ENCODING.add(ffmpeg);
        }
        WitchsAtelier.LOGGER.info("[visualcheck] filmed {} ({} frames)", file.getName(), written);
        ffmpeg = null;
    }

    static void finishAll() {
        synchronized (ENCODING) {
            for (Process p : ENCODING) {
                try {
                    p.waitFor();
                } catch (InterruptedException ignored) {
                }
            }
            ENCODING.clear();
        }
    }
}
