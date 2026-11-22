package com.fimkov.witchsatelier.client.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.fimkov.witchsatelier.WitchsAtelier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Screenshot harness only: films what the game shows into an mp4 by handing each rendered frame to ffmpeg. Frames
 * are paced by the clock (repeated when the game draws slower than the film runs), so the film plays in real time.
 */
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

    /** Called once each frame, after it is drawn. */
    void frame() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (ffmpeg == null) start = now;
        long due = (now - start) * FPS / 1_000_000_000L + 1;
        if (ffmpeg != null && written >= due) return;
        try (NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            if (ffmpeg == null) open(img.getWidth(), img.getHeight());
            if (img.getWidth() != w || img.getHeight() != h) return; // the window changed size: skip the frame
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

    private void open(int width, int height) throws java.io.IOException {
        w = width;
        h = height;
        buf = new byte[w * h * 4];
        ffmpeg = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", w + "x" + h,
                "-r", String.valueOf(FPS), "-i", "-", "-vf", "scale=1280:-2", "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart", file.getAbsolutePath())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        out = new java.io.BufferedOutputStream(ffmpeg.getOutputStream(), 1 << 22);
    }

    /** Ends the film; ffmpeg finishes it in the background. */
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

    /** Waits for every film to be written out (before the game quits). */
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
