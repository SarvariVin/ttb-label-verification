import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Generates synthetic, fictional alcohol labels for demos and pipeline benchmarks.
 * Run: java scripts/SampleLabelGenerator.java test-labels
 * <p>
 * Every brand, company and address here is invented. Each label folder gets
 * front.png and application.json (the Form 5100.31 values an applicant would declare).
 */
public class SampleLabelGenerator {

    static final String WARNING = "GOVERNMENT WARNING: (1) According to the Surgeon General, women should not "
            + "drink alcoholic beverages during pregnancy because of the risk of birth defects. (2) Consumption "
            + "of alcoholic beverages impairs your ability to drive a car or operate machinery, and may cause "
            + "health problems.";

    record Spec(String folder, String type, int sizeMl, Color bg, Color ink,
                String brand, String fanciful, String classType, String abv, String net,
                String phrase, String address, String extraLine, String warning,
                Map<String, String> application) {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "test-labels");
        List<Spec> specs = List.of(
                new Spec("aldercrest-bourbon", "DISTILLED_SPIRITS", 750, new Color(0xF4EAD5), new Color(0x3B2314),
                        "ALDERCREST", "Small Batch", "Kentucky Straight Bourbon Whiskey", "45% Alc./Vol. (90 Proof)",
                        "750 mL", "Distilled and Bottled by", "Aldercrest Distilling Co., Bardstown, Kentucky",
                        "Aged 6 Years", WARNING,
                        app("Aldercrest", "Small Batch", "Kentucky Straight Bourbon Whiskey", "45% Alc./Vol.",
                                "750 mL", "Distilled and Bottled by",
                                "Aldercrest Distilling Co., Bardstown, Kentucky", "ageStatement", "Aged 6 Years")),
                new Spec("tidewater-lager", "MALT_BEVERAGE", 355, new Color(0xE3EEF7), new Color(0x0E2A47),
                        "TIDEWATER ROW", "Harbor Lager", "Lager", "5.0% Alc./Vol.", "12 FL OZ",
                        "Brewed and Packaged by", "Tidewater Row Brewing Co., Portland, Maine", null, WARNING,
                        app("Tidewater Row", "Harbor Lager", "Lager", "5.0% Alc./Vol.", "12 FL OZ",
                                "Brewed and Packaged by", "Tidewater Row Brewing Co., Portland, Maine", null, null)),
                new Spec("quillmoor-chardonnay", "WINE", 750, new Color(0xFBF8EF), new Color(0x2F3B1F),
                        "QUILLMOOR CELLARS", null, "Chardonnay", "13.5% Alc. by Vol.", "750 mL",
                        "Produced and Bottled by", "Quillmoor Cellars, Healdsburg, California",
                        "Sonoma Coast  ·  2022  ·  Contains Sulfites", WARNING,
                        app("Quillmoor Cellars", null, "Chardonnay", "13.5% Alc. by Vol.", "750 mL",
                                "Produced and Bottled by", "Quillmoor Cellars, Healdsburg, California",
                                "appellationOfOrigin", "Sonoma Coast")),
                // Deliberately non-compliant: ABV differs from the application and the
                // warning prefix is not in capitals → exercises the correction path.
                new Spec("northvale-vodka-flawed", "DISTILLED_SPIRITS", 750, new Color(0xECEFF3), new Color(0x1C2230),
                        "NORTHVALE", null, "Vodka", "40% Alc./Vol. (80 Proof)", "750 mL", "Bottled by",
                        "Northvale Spirits, Duluth, Minnesota", "Distilled from Grain",
                        WARNING.replace("GOVERNMENT WARNING:", "Government Warning:"),
                        app("Northvale", null, "Vodka", "42% Alc./Vol.", "750 mL", "Bottled by",
                                "Northvale Spirits, Duluth, Minnesota", null, null)));

        for (Spec s : specs) {
            Path dir = out.resolve(s.folder());
            Files.createDirectories(dir);
            ImageIO.write(render(s), "png", dir.resolve("front.png").toFile());
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("beverageType", s.type());
            json.put("containerSizeMl", s.sizeMl());
            json.putAll(s.application());
            Files.writeString(dir.resolve("application.json"), toJson(json));
            System.out.println("wrote " + dir);
        }
    }

    static Map<String, String> app(String brand, String fanciful, String classType, String abv, String net,
                                   String phrase, String address, String extraKey, String extraValue) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("brandName", brand);
        if (fanciful != null) {
            m.put("fancifulName", fanciful);
        }
        m.put("classType", classType);
        m.put("alcoholContent", abv);
        m.put("netContents", net);
        m.put("qualifyingPhrase", phrase);
        m.put("nameAndAddress", address);
        if (extraKey != null) {
            m.put(extraKey, extraValue);
        }
        return m;
    }

    /**
     * Label design (v2): a single rounded frame with corner ornaments, an accent band at the top,
     * a sans-serif brand, a diamond divider, ABV and net contents on two tinted pills, and the
     * health warning in its own ruled panel. Only the regulated text is drawn as text, so OCR
     * and pre-fill see exactly the same words as before.
     */
    static BufferedImage render(Spec s) {
        int w = 1600, h = 2000, margin = 110;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(s.bg());
        g.fillRect(0, 0, w, h);

        // Frame: one rounded border with a diamond ornament in each corner.
        Color accent = blend(s.ink(), s.bg(), 0.35);
        g.setColor(s.ink());
        g.setStroke(new BasicStroke(6));
        g.drawRoundRect(50, 50, w - 100, h - 100, 60, 60);
        for (int[] c : new int[][]{{50, 50}, {w - 50, 50}, {50, h - 50}, {w - 50, h - 50}}) {
            diamond(g, c[0], c[1], 22, s.ink());
        }

        // Accent band across the top (decoration only, no text).
        g.setColor(accent);
        g.fillRoundRect(110, 110, w - 220, 36, 36, 36);

        g.setColor(s.ink());
        int y = 380;
        y = centered(g, s.brand(), new Font("SansSerif", Font.BOLD, 140), w, y);
        if (s.fanciful() != null) {
            y = centered(g, s.fanciful(), new Font("Serif", Font.ITALIC, 78), w, y + 100);
        }

        // Divider: two rules with a diamond between them.
        int dy = y + 70;
        g.setStroke(new BasicStroke(3));
        g.drawLine(margin * 3, dy, w / 2 - 40, dy);
        g.drawLine(w / 2 + 40, dy, w - margin * 3, dy);
        diamond(g, w / 2, dy, 16, s.ink());

        y = centered(g, s.classType(), new Font("SansSerif", Font.BOLD, 70), w, dy + 150);
        if (s.extraLine() != null) {
            y = centered(g, s.extraLine(), new Font("SansSerif", Font.PLAIN, 56), w, y + 90);
        }

        // ABV and net contents in two pill boxes on one baseline.
        Font pillFont = new Font("SansSerif", Font.BOLD, 60);
        g.setFont(pillFont);
        FontMetrics pm = g.getFontMetrics();
        int gap = 60, padX = 44, pillH = pm.getHeight() + 36;
        int aw = pm.stringWidth(s.abv()) + 2 * padX, nw = pm.stringWidth(s.net()) + 2 * padX;
        int px = (w - (aw + gap + nw)) / 2, baseline = y + 190;
        int top = baseline - pm.getAscent() - 18;
        // Soft tint, no outline: a dark border around text makes Tesseract's layout analysis skip it.
        g.setColor(blend(s.ink(), s.bg(), 0.10));
        g.fillRoundRect(px, top, aw, pillH, pillH, pillH);
        g.fillRoundRect(px + aw + gap, top, nw, pillH, pillH, pillH);
        g.setColor(s.ink());
        g.drawString(s.abv(), px + padX, baseline);
        g.drawString(s.net(), px + aw + gap + padX, baseline);
        y = baseline;

        y = centered(g, s.phrase(), new Font("SansSerif", Font.PLAIN, 50), w, y + 170);
        y = centered(g, s.address(), new Font("SansSerif", Font.PLAIN, 50), w, y + 70);

        // Health warning in a ruled panel: bold prefix (required), regular body, wrapped.
        Font body = new Font("SansSerif", Font.PLAIN, 40);
        g.setFont(body);
        FontMetrics fm = g.getFontMetrics();
        int x = margin + 50, maxWidth = w - 2 * (margin + 50);
        List<String> lines = wrap(s.warning(), fm, maxWidth);
        int panelH = lines.size() * (fm.getHeight() + 4) + 70;
        int panelTop = h - 170 - panelH;
        g.setColor(blend(s.ink(), s.bg(), 0.08));
        g.fillRoundRect(margin, panelTop, w - 2 * margin, panelH, 28, 28);
        g.setColor(s.ink());
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(margin, panelTop, w - 2 * margin, panelH, 28, 28);

        int wy = panelTop + 35 + fm.getAscent();
        String prefix = s.warning().substring(0, s.warning().indexOf(':') + 1);
        boolean first = true;
        for (String line : lines) {
            if (first && line.startsWith(prefix)) {
                // The "GOVERNMENT WARNING:" prefix must be bold (27 CFR 16.22); the body must not be.
                Font bold = body.deriveFont(Font.BOLD);
                g.setFont(bold);
                g.drawString(prefix, x, wy);
                int bx = x + g.getFontMetrics().stringWidth(prefix);
                g.setFont(body);
                g.drawString(line.substring(prefix.length()), bx, wy);
            } else {
                g.drawString(line, x, wy);
            }
            first = false;
            wy += fm.getHeight() + 4;
        }
        g.dispose();
        return img;
    }

    /** A filled diamond centred on (cx, cy). */
    static void diamond(Graphics2D g, int cx, int cy, int r, Color color) {
        g.setColor(color);
        g.fillPolygon(new int[]{cx, cx + r, cx, cx - r}, new int[]{cy - r, cy, cy + r, cy}, 4);
    }

    /** Mixes {@code a} into {@code b}: 0 gives b, 1 gives a. */
    static Color blend(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() * t + b.getRed() * (1 - t)),
                (int) Math.round(a.getGreen() * t + b.getGreen() * (1 - t)),
                (int) Math.round(a.getBlue() * t + b.getBlue() * (1 - t)));
    }

    /** Draws centered text, shrinking the font until it fits inside the border. */
    static int centered(Graphics2D g, String text, Font font, int width, int y) {
        Font f = font;
        while (g.getFontMetrics(f).stringWidth(text) > width - 300 && f.getSize() > 20) {
            f = f.deriveFont((float) f.getSize() - 4);
        }
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (width - fm.stringWidth(text)) / 2, y);
        return y;
    }

    static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    static String toJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            sb.append("  \"").append(e.getKey()).append("\": ");
            Object v = e.getValue();
            sb.append(v instanceof Number ? v.toString() : "\"" + v.toString().replace("\"", "\\\"") + "\"");
            sb.append(++i < m.size() ? ",\n" : "\n");
        }
        return sb.append("}\n").toString();
    }
}
