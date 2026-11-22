package com.cliptova.clip;

import com.cliptova.transcription.Transcript;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ClipCaptions {
    private static final int WIDTH = 720;
    private static final int HEIGHT = 230;
    private static final int LINES_PER_PAGE = 3;
    private static final int MARGIN = 56;
    private static final int LINE_GAP = 7;
    private static final String BLANK = "blank.png";

    private ClipCaptions() {}

    static Path write(Path directory, List<Transcript.Segment> segments, ClipStore.Claim clip) throws IOException {
        var sheet = new StringBuilder("ffconcat version 1.0\n");
        var blank = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(blank, "png", directory.resolve(BLANK).toFile());
        long playhead = 0;
        int nextFile = 0;
        var settings = clip.captions();
        Font font = settings.font();
        for (Cue cue : cues(segments, clip.startMs(), clip.endMs())) {
            addFrame(sheet, BLANK, cue.startMs() - playhead);
            var measurerContext = blank.createGraphics();
            List<TextLayout> lines;
            try {
                configure(measurerContext);
                lines = wrap(cue.text(), measurerContext, font);
            } finally {
                measurerContext.dispose();
            }
            var pages = paginate(lines);
            long span = cue.endMs() - cue.startMs();
            for (int page = 0; page < pages.size(); page++) {
                long duration = span * (page + 1) / pages.size() - span * page / pages.size();
                if (duration == 0) continue;
                String name = "caption-" + nextFile++ + ".png";
                writeFrame(directory.resolve(name), pages.get(page), settings);
                addFrame(sheet, name, duration);
            }
            playhead = cue.endMs();
        }
        addFrame(sheet, BLANK, clip.endMs() - clip.startMs() - playhead);
        // The last file's timestamp gives the preceding frame its full duration.
        addFrame(sheet, BLANK, 1);
        return Files.writeString(directory.resolve("captions.ffconcat"), sheet, StandardCharsets.UTF_8);
    }

    static List<Cue> cues(List<Transcript.Segment> segments, long startMs, long endMs) {
        var cues = new ArrayList<Cue>();
        long previousEnd = 0;
        for (var segment : segments) {
            long start = Math.max(previousEnd, segment.startMs() - startMs);
            long end = Math.min(endMs, segment.endMs()) - startMs;
            if (end <= start) continue;
            String text = segment.text().replaceAll("(?U)\\s+", " ").strip();
            if (text.isEmpty()) continue;
            cues.add(new Cue(start, end, text));
            previousEnd = end;
        }
        return cues;
    }

    private static void addFrame(StringBuilder sheet, String name, long durationMs) {
        if (durationMs <= 0) return;
        sheet.append("file ").append(name).append("\noption framerate 1000\n")
                .append("duration ").append(String.format(Locale.ROOT, "%.3f", durationMs / 1000.0)).append('\n');
    }

    private static List<TextLayout> wrap(String text, Graphics2D graphics, Font font) {
        var words = new AttributedString(text);
        words.addAttribute(TextAttribute.FONT, font);
        var measurer = new LineBreakMeasurer(words.getIterator(), graphics.getFontRenderContext());
        var lines = new ArrayList<TextLayout>();
        while (measurer.getPosition() < text.length()) {
            lines.add(measurer.nextLayout(WIDTH - 2 * MARGIN));
        }
        return lines;
    }

    private static List<List<TextLayout>> paginate(List<TextLayout> lines) {
        var pages = new ArrayList<List<TextLayout>>();
        int start = 0;
        while (start < lines.size()) {
            int end = start + 1;
            float height = lineHeight(lines.get(start));
            while (end < lines.size() && end - start < LINES_PER_PAGE
                    && height + lineHeight(lines.get(end)) <= HEIGHT - 32) {
                height += lineHeight(lines.get(end++));
            }
            pages.add(lines.subList(start, end));
            start = end;
        }
        return pages;
    }

    private static float lineHeight(TextLayout line) {
        return line.getAscent() + line.getDescent() + line.getLeading() + LINE_GAP;
    }

    private static float left(float width, CaptionSettings.Alignment alignment) {
        return switch (alignment) {
            case LEFT -> MARGIN;
            case CENTER -> (WIDTH - width) / 2;
            case RIGHT -> WIDTH - MARGIN - width;
        };
    }

    private static void writeFrame(Path file, List<TextLayout> lines, CaptionSettings settings) throws IOException {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            configure(graphics);
            float totalHeight = 0;
            float widest = 0;
            for (TextLayout line : lines) {
                totalHeight += lineHeight(line);
                widest = Math.max(widest, line.getAdvance());
            }
            float top = (HEIGHT - totalHeight) / 2;
            if (settings.backgroundOpacity() > 0) {
                var fill = Color.decode(settings.backgroundColor());
                graphics.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(),
                        Math.round(settings.backgroundOpacity() * 255 / 100f)));
                graphics.fillRoundRect(Math.round(left(widest, settings.alignment())) - 18, Math.round(top) - 10,
                        (int) Math.ceil(widest) + 36, (int) Math.ceil(totalHeight) + 13, 20, 20);
            }
            var textColor = Color.decode(settings.textColor());
            var outlineColor = Color.decode(settings.outlineColor());
            if (settings.outlineWidth() > 0) {
                graphics.setStroke(new BasicStroke(settings.outlineWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            }
            for (TextLayout line : lines) {
                float x = left(line.getAdvance(), settings.alignment());
                top += line.getAscent();
                if (settings.outlineWidth() > 0) {
                    var outline = line.getOutline(AffineTransform.getTranslateInstance(x, top));
                    graphics.setColor(outlineColor);
                    graphics.draw(outline);
                }
                graphics.setColor(textColor);
                line.draw(graphics, x, top);
                top += line.getDescent() + line.getLeading() + LINE_GAP;
            }
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    record Cue(long startMs, long endMs, String text) {}
}
