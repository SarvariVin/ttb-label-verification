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

    static BufferedImage render(Spec s) {
        int w = 1600, h = 2000, margin = 110;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(s.bg());
        g.fillRect(0, 0, w, h);
        g.setColor(s.ink());
        g.setStroke(new BasicStroke(8));
        g.drawRect(40, 40, w - 80, h - 80);
        g.setStroke(new BasicStroke(2));
        g.drawRect(60, 60, w - 120, h - 120);

        int y = 330;
        y = centered(g, s.brand(), new Font("Serif", Font.BOLD, 150), w, y);
        if (s.fanciful() != null) {
            y = centered(g, s.fanciful(), new Font("Serif", Font.ITALIC, 80), w, y + 60);
        }
        g.drawLine(margin * 3, y + 50, w - margin * 3, y + 50);
        y = centered(g, s.classType(), new Font("SansSerif", Font.BOLD, 70), w, y + 170);
        if (s.extraLine() != null) {
            y = centered(g, s.extraLine(), new Font("SansSerif", Font.PLAIN, 56), w, y + 90);
        }
        y = centered(g, s.abv() + "     " + s.net(), new Font("SansSerif", Font.BOLD, 64), w, y + 160);
        y = centered(g, s.phrase(), new Font("SansSerif", Font.PLAIN, 50), w, y + 150);
        y = centered(g, s.address(), new Font("SansSerif", Font.PLAIN, 50), w, y + 70);

        // Health warning block: bold prefix (required), regular body, wrapped.
        Font body = new Font("SansSerif", Font.PLAIN, 40);
        g.setFont(body);
        FontMetrics fm = g.getFontMetrics();
        int x = margin + 40, maxWidth = w - 2 * (margin + 40);
        int wy = h - 480;
        String prefix = s.warning().substring(0, s.warning().indexOf(':') + 1);
        boolean first = true;
        for (String line : wrap(s.warning(), fm, maxWidth)) {
            if (first && line.startsWith(prefix)) {
                // The "GOVERNMENT WARNING:" prefix must be bold (27 CFR 16.22); the body must not be.
                Font bold = body.deriveFont(Font.BOLD);
                g.setFont(bold);
                g.drawString(prefix, x, wy);
                int px = x + g.getFontMetrics().stringWidth(prefix);
                g.setFont(body);
                g.drawString(line.substring(prefix.length()), px, wy);
            } else {
                g.drawString(line, x, wy);
            }
            first = false;
            wy += fm.getHeight() + 4;
        }
        g.dispose();
        return img;
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
