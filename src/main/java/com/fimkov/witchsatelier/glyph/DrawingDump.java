package com.fimkov.witchsatelier.glyph;

import com.fimkov.witchsatelier.glyph.recognize.GlyphReading;
import com.fimkov.witchsatelier.glyph.surface.InkStroke;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * In a development game only: every glyph whose ring closes is written to {@code witchsatelier-drawings/} in the game
 * folder with what it was read as, the way the recogniser tests load drawings, so a real drawing that was misread
 * can become a test.
 */
public final class DrawingDump {
    private DrawingDump() {}

    static void save(List<InkStroke> strokes, double upAngle, GlyphReading reading) {
        List<List<com.fimkov.witchsatelier.glyph.recognize.Pt>> pts = new java.util.ArrayList<>();
        for (InkStroke s : strokes) pts.add(s.points());
        savePoints(pts, GlyphService.INK_WIDTH, upAngle, reading, "");
    }

    /** A drawing on a page (paper, a book, a palm quire), in page units. */
    public static void savePage(List<List<com.fimkov.witchsatelier.glyph.recognize.Pt>> strokes, double inkWidth, GlyphReading reading) {
        savePoints(strokes, inkWidth, Math.PI / 2, reading, "page_");
    }

    private static void savePoints(List<List<com.fimkov.witchsatelier.glyph.recognize.Pt>> strokes, double inkWidth, double upAngle, GlyphReading reading, String prefix) {
        if (FMLEnvironment.production || strokes.isEmpty()) return;
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("witchsatelier-drawings");
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            sb.append("#ink ").append(inkWidth).append('\n');
            sb.append("#up ").append(String.format(java.util.Locale.ROOT, "%.1f", Math.toDegrees(upAngle))).append('\n');
            sb.append("# read as: ").append(reading.describe().replace('\n', ' ')).append('\n');
            for (var s : strokes) {
                for (int i = 0; i < s.size(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(String.format(java.util.Locale.ROOT, "%.4f %.4f", s.get(i).x(), s.get(i).y()));
                }
                sb.append('\n');
            }
            String what = reading.sigil() != null ? reading.sigil().name().toLowerCase(java.util.Locale.ROOT)
                    : reading.centerSign() != null ? reading.centerSign().name().toLowerCase(java.util.Locale.ROOT) : "nothing";
            String name = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new java.util.Date()) + "_" + prefix + what + "_" + reading.signs().size() + "signs.txt";
            Files.writeString(dir.resolve(name), sb.toString());
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().debug("could not save the drawing", e);
        }
    }
}
