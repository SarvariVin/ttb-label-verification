package gov.ttb.labelverification.ai;

import gov.ttb.labelverification.regulatory.BeverageType;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Keyword-based beverage type detection from OCR text. */
public final class BeverageDetector {

    private static final Map<BeverageType, List<String>> KEYWORDS = new EnumMap<>(Map.of(
            BeverageType.DISTILLED_SPIRITS, List.of("whiskey", "whisky", "bourbon", "vodka", "gin", "rum", "tequila",
                    "mezcal", "brandy", "cognac", "scotch", "proof", "distilled by", "distilled from",
                    "straight bourbon", "single malt", "rye whiskey", "liqueur", "cordial", "absinthe", "moonshine"),
            BeverageType.WINE, List.of("wine", "cabernet", "chardonnay", "merlot", "pinot", "sauvignon", "riesling",
                    "zinfandel", "syrah", "shiraz", "malbec", "tempranillo", "moscato", "prosecco", "champagne",
                    "vintage", "contains sulfites", "sulfites", "vinted", "cellared", "estate bottled", "appellation"),
            BeverageType.MALT_BEVERAGE, List.of("beer", "ale", "lager", "ipa", "stout", "porter", "pilsner",
                    "malt beverage", "brewed by", "brewery", "brewing", "hops", "hard seltzer", "hard tea")));

    private BeverageDetector() {
    }

    /** Returns the type with the most whole-word keyword hits, or null when there are none. */
    public static BeverageType detect(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        BeverageType best = null;
        int bestScore = 0;
        for (Map.Entry<BeverageType, List<String>> e : KEYWORDS.entrySet()) {
            int score = 0;
            for (String kw : e.getValue()) {
                if (Pattern.compile("\\b" + Pattern.quote(kw) + "\\b").matcher(lower).find()) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return best;
    }
}
